/*
 *  Copyright 2012-2015 Viant.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy of
 *  the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package com.caucho.hessian.io;

import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.hessian.HessianUnshared;

import sun.misc.Unsafe;

/**
 * Serializing an object for known object types.
 */
public class UnsafeSerializer extends AbstractSerializer
{
  private static final Logger log
    = Logger.getLogger(UnsafeSerializer.class.getName());

  private static boolean _isEnabled;
  private static final Unsafe _unsafe;
  
  private static final WeakHashMap<Class<?>,SoftReference<UnsafeSerializer>> _serializerMap
    = new WeakHashMap<Class<?>,SoftReference<UnsafeSerializer>>();

  private static Object []NULL_ARGS = new Object[0];

  private Field []_fields;
  private FieldSerializer []_fieldSerializers;
  
  public static boolean isEnabled()
  {
    return _isEnabled;
  }

  public UnsafeSerializer(Class<?> cl)
  {
    introspect(cl);
  }

  public static UnsafeSerializer create(Class<?> cl)
  {
    ClassLoader loader = cl.getClassLoader();

    synchronized (_serializerMap) {
      SoftReference<UnsafeSerializer> baseRef
        = _serializerMap.get(cl);

      UnsafeSerializer base = baseRef != null ? baseRef.get() : null;

      if (base == null) {
        if (cl.isAnnotationPresent(HessianUnshared.class))
          base = new UnsafeUnsharedSerializer(cl);
        else
          base = new UnsafeSerializer(cl);
        
        baseRef = new SoftReference<UnsafeSerializer>(base);
        _serializerMap.put(cl, baseRef);
      }

      return base;
    }
  }

  protected void introspect(Class<?> cl)
  {
    ArrayList<Field> primitiveFields = new ArrayList<Field>();
    ArrayList<Field> compoundFields = new ArrayList<Field>();

    for (; cl != null; cl = cl.getSuperclass()) {
      Field []fields = cl.getDeclaredFields();
      for (int i = 0; i < fields.length; i++) {
        Field field = fields[i];

        if (Modifier.isTransient(field.getModifiers())
            || Modifier.isStatic(field.getModifiers()))
          continue;

        // XXX: could parameterize the handler to only deal with public
        field.setAccessible(true);

        if (field.getType().isPrimitive()
            || (field.getType().getName().startsWith("java.lang.")
                && ! field.getType().equals(Object.class)))
          primitiveFields.add(field);
        else
          compoundFields.add(field);
      }
    }

    ArrayList<Field> fields = new ArrayList<Field>();
    fields.addAll(primitiveFields);
    fields.addAll(compoundFields);

    _fields = new Field[fields.size()];
    fields.toArray(_fields);

    _fieldSerializers = new FieldSerializer[_fields.length];

    for (int i = 0; i < _fields.length; i++) {
      _fieldSerializers[i] = getFieldSerializer(_fields[i]);
    }
  }

  @Override
  public void writeObject(Object obj, AbstractHessianOutput out)
    throws IOException
  {
    if (out.addRef(obj)) {
      return;
    }
    
    Class<?> cl = obj.getClass();

    int ref = out.writeObjectBegin(cl.getName());

    if (ref >= 0) {
      writeInstance(obj, out);
    }
    else if (ref == -1) {
      writeDefinition20(out);
      out.writeObjectBegin(cl.getName());
      writeInstance(obj, out);
    }
    else {
      writeObject10(obj, out);
    }
  }

  protected void writeObject10(Object obj, AbstractHessianOutput out)
    throws IOException
  {
    for (int i = 0; i < _fields.length; i++) {
      Field field = _fields[i];

      out.writeString(field.getName());

      _fieldSerializers[i].serialize(out, obj);
    }

    out.writeMapEnd();
  }

  private void writeDefinition20(AbstractHessianOutput out)
    throws IOException
  {
    out.writeClassFieldLength(_fields.length);

    for (int i = 0; i < _fields.length; i++) {
      Field field = _fields[i];

      out.writeString(field.getName());
    }
  }

  final public void writeInstance(Object obj, AbstractHessianOutput out)
    throws IOException
  {
    try {
      FieldSerializer []fieldSerializers = _fieldSerializers;
      int length = fieldSerializers.length;
      
      for (int i = 0; i < length; i++) {
        fieldSerializers[i].serialize(out, obj);
      }
    } catch (RuntimeException e) {
      throw new RuntimeException(e.getMessage() + "\n class: "
                                 + obj.getClass().getName()
                                 + " (object=" + obj + ")",
                                 e);
    } catch (IOException e) {
      throw new IOExceptionWrapper(e.getMessage() + "\n class: "
                                   + obj.getClass().getName()
                                   + " (object=" + obj + ")",
                                   e);
    }
  }

  private static FieldSerializer getFieldSerializer(Field field)
  {
    Class<?> type = field.getType();
    
    if (boolean.class.equals(type)) {
      return new BooleanFieldSerializer(field);
    }
    else if (byte.class.equals(type)) {
      return new ByteFieldSerializer(field);
    }
    else if (char.class.equals(type)) {
      return new CharFieldSerializer(field);
    }
    else if (short.class.equals(type)) {
      return new ShortFieldSerializer(field);
    }
    else if (int.class.equals(type)) {
      return new IntFieldSerializer(field);
    }
    else if (long.class.equals(type)) {
      return new LongFieldSerializer(field);
    }
    else if (double.class.equals(type)) {
      return new DoubleFieldSerializer(field);
    }
    else if (float.class.equals(type)) {
      return new FloatFieldSerializer(field);
    }
    else if (String.class.equals(type)) {
      return new StringFieldSerializer(field);
    }
    else if (java.util.Date.class.equals(type)
             || java.sql.Date.class.equals(type)
             || java.sql.Timestamp.class.equals(type)
             || java.sql.Time.class.equals(type)) {
      return new DateFieldSerializer(field);
    }
    else
      return new ObjectFieldSerializer(field);
  }

  abstract static class FieldSerializer {
    abstract void serialize(AbstractHessianOutput out, Object obj)
      throws IOException;
  }

  final static class ObjectFieldSerializer extends FieldSerializer {
    private final Field _field;
    private final long _offset;
    
    ObjectFieldSerializer(Field field)
    {
      _field = field;
      _offset = _unsafe.objectFieldOffset(field);
      
      if (_offset == Unsafe.INVALID_FIELD_OFFSET)
        throw new IllegalStateException();
    }

    @Override
    final void serialize(AbstractHessianOutput out, Object obj)
      throws IOException
    {
      try {
        Object value = _unsafe.getObject(obj, _offset);
        
        out.writeObject(value);
      } catch (RuntimeException e) {
        throw new RuntimeException(e.getMessage() + "\n field: "
                                   + _field.getDeclaringClass().getName()
                                   + '.' + _field.getName(),
                                   e);
      } catch (IOException e) {
        throw new IOExceptionWrapper(e.getMessage() + "\n field: "
                                     + _field.getDeclaringClass().getName()
                                     + '.' + _field.getName(),
                                     e);
      }
    }
  }

  final static class BooleanFieldSerializer extends FieldSerializer {
    private final Field _field;
    private final long _offset;
    
    BooleanFieldSerializer(Field field)
    {
      _field = field;
      _offset = _unsafe.objectFieldOffset(field);
      
      if (_offset == Unsafe.INVALID_FIELD_OFFSET)
        throw new IllegalStateException();
    }

    void serialize(AbstractHessianOutput out, Object obj)
      throws IOException
    {
      boolean value = _unsafe.getBoolean(obj, _offset);
      
      out.writeBoolean(value);
    }
  }

  final static class ByteFieldSerializer extends FieldSerializer {
    private final Field _field;
    private final long _offset;
    
    ByteFieldSerializer(Field field)
    {
      _field = field;
      _offset = _unsafe.objectFieldOffset(field);
      
      if (_offset == Unsafe.INVALID_FIELD_OFFSET)
        throw new IllegalStateException();
    }
    
    final void serialize(AbstractHessianOutput out, Object obj)
      throws IOException
    {
      int value = _unsafe.getByte(obj, _offset);

      out.writeInt(value);
    }
  }

  final static class CharFieldSerializer extends FieldSerializer {
    private final Field _field;
    private final long _offset;
    
    CharFieldSerializer(Field field)
    {
      _field = field;
      _offset = _unsafe.objectFieldOffset(field);
      
      if (_offset == Unsafe.INVALID_FIELD_OFFSET)
        throw new IllegalStateException();
    }
    
    final void serialize(AbstractHessianOutput out, Object obj)
      throws IOException
    {
      char value = _unsafe.getChar(obj, _offset);

      out.writeString(String.valueOf(value));
    }
  }

  final static class ShortFieldSerializer extends FieldSerializer {
    private final Field _field;
    private final long _offset;
    
    ShortFieldSerializer(Field field)
    {
      _field = field;
      _offset = _unsafe.objectFieldOffset(field);
      
      if (_offset == Unsafe.INVALID_FIELD_OFFSET)
        throw new IllegalStateException();
    }
    
    final void serialize(AbstractHessianOutput out, Object obj)
      throws IOException
    {
      int value = _unsafe.getShort(obj, _offset);

      out.writeInt(value);
    }
  }

  final static class IntFieldSerializer extends FieldSerializer {
    private final Field _field;
    private final long _offset;
    
    IntFieldSerializer(Field field)
    {
      _field = field;
      _offset = _unsafe.objectFieldOffset(field);
      
      if (_offset == Unsafe.INVALID_FIELD_OFFSET)
        throw new IllegalStateException();
    }
    
    final void serialize(AbstractHessianOutput out, Object obj)
      throws IOException
    {
      int value = _unsafe.getInt(obj, _offset);

      out.writeInt(value);
    }
  }

  final static class LongFieldSerializer extends FieldSerializer {
    private final Field _field;
    private final long _offset;
    
    LongFieldSerializer(Field field)
    {
      _field = field;
      _offset = _unsafe.objectFieldOffset(field);
      
      if (_offset == Unsafe.INVALID_FIELD_OFFSET)
        throw new IllegalStateException();
    }
    
    final void serialize(AbstractHessianOutput out, Object obj)
      throws IOException
    {
      long value = _unsafe.getLong(obj, _offset);

      out.writeLong(value);
    }
  }

  final static class FloatFieldSerializer extends FieldSerializer {
    private final Field _field;
    private final long _offset;
    
    FloatFieldSerializer(Field field)
    {
      _field = field;
      _offset = _unsafe.objectFieldOffset(field);
      
      if (_offset == Unsafe.INVALID_FIELD_OFFSET)
        throw new IllegalStateException();
    }
    
    final void serialize(AbstractHessianOutput out, Object obj)
      throws IOException
    {
      double value = _unsafe.getFloat(obj, _offset);

      out.writeDouble(value);
    }
  }

  final static class DoubleFieldSerializer extends FieldSerializer {
    private final Field _field;
    private final long _offset;
    
    DoubleFieldSerializer(Field field)
    {
      _field = field;
      _offset = _unsafe.objectFieldOffset(field);
      
      if (_offset == Unsafe.INVALID_FIELD_OFFSET)
        throw new IllegalStateException();
    }
    
    final void serialize(AbstractHessianOutput out, Object obj)
      throws IOException
    {
      double value = _unsafe.getDouble(obj, _offset);

      out.writeDouble(value);
    }
  }

  final static class StringFieldSerializer extends FieldSerializer {
    private final Field _field;
    private final long _offset;
    
    StringFieldSerializer(Field field)
    {
      _field = field;
      _offset = _unsafe.objectFieldOffset(field);
      
      if (_offset == Unsafe.INVALID_FIELD_OFFSET)
        throw new IllegalStateException();
    }
    
    @Override
    final void serialize(AbstractHessianOutput out, Object obj)
      throws IOException
    {
      String value = (String) _unsafe.getObject(obj, _offset);

      out.writeString(value);
    }
  }

  final static class DateFieldSerializer extends FieldSerializer {
    private final Field _field;
    private final long _offset;
    
    DateFieldSerializer(Field field)
    {
      _field = field;
      _offset = _unsafe.objectFieldOffset(field);
      
      if (_offset == Unsafe.INVALID_FIELD_OFFSET)
        throw new IllegalStateException();
    }

    @Override
    void serialize(AbstractHessianOutput out, Object obj)
      throws IOException
    {
      java.util.Date value
        = (java.util.Date) _unsafe.getObject(obj, _offset);

      if (value == null)
        out.writeNull();
      else
        out.writeUTCDate(value.getTime());
    }
  }
  
  static {
    boolean isEnabled = false;
    Unsafe unsafe = null;
    
    try {
      Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
      Field theUnsafe = null;
      for (Field field : unsafeClass.getDeclaredFields()) {
        if (field.getName().equals("theUnsafe"))
          theUnsafe = field;
      }
      
      if (theUnsafe != null) {
        theUnsafe.setAccessible(true);
        unsafe = (Unsafe) theUnsafe.get(null);
      }
      
      isEnabled = unsafe != null;
      
      String unsafeProp = System.getProperty("com.caucho.hessian.unsafe");
      
      if ("false".equals(unsafeProp))
        isEnabled = false;
    } catch (Throwable e) {
      log.log(Level.ALL, e.toString(), e);
    }
    
    _unsafe = unsafe;
    _isEnabled = isEnabled;
  }
}
