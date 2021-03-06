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

package com.caucho.hessian.jmx;

import com.caucho.hessian.io.AbstractSerializerFactory;
import com.caucho.hessian.io.Deserializer;
import com.caucho.hessian.io.HessianProtocolException;
import com.caucho.hessian.io.Serializer;
import com.caucho.hessian.io.StringValueDeserializer;
import com.caucho.hessian.io.StringValueSerializer;

import javax.management.*;

/**
 * Serializers for JMX classes.
 */
public class JMXSerializerFactory extends AbstractSerializerFactory {
  /**
   * Returns the serializer for a class.
   *
   * @param cl the class of the object that needs to be serialized.
   *
   * @return a serializer object for the serialization.
   */
  public Serializer getSerializer(Class cl)
    throws HessianProtocolException
  {
    if (ObjectName.class.equals(cl)) {
      return new StringValueSerializer();
    }
    
    return null;
  }
  
  /**
   * Returns the deserializer for a class.
   *
   * @param cl the class of the object that needs to be deserialized.
   *
   * @return a deserializer object for the serialization.
   */
  public Deserializer getDeserializer(Class cl)
    throws HessianProtocolException
  {
    if (ObjectName.class.equals(cl)) {
      return new StringValueDeserializer(cl);
    }
    else if (ObjectInstance.class.equals(cl)) {
      return new ObjectInstanceDeserializer();
    }
    else if (MBeanAttributeInfo.class.isAssignableFrom(cl)) {
      return new MBeanAttributeInfoDeserializer();
    }
    else if (MBeanConstructorInfo.class.isAssignableFrom(cl)) {
      return new MBeanConstructorInfoDeserializer();
    }
    else if (MBeanOperationInfo.class.isAssignableFrom(cl)) {
      return new MBeanOperationInfoDeserializer();
    }
    else if (MBeanParameterInfo.class.isAssignableFrom(cl)) {
      return new MBeanParameterInfoDeserializer();
    }
    else if (MBeanNotificationInfo.class.isAssignableFrom(cl)) {
      return new MBeanNotificationInfoDeserializer();
    }
    /*
    else if (MBeanInfo.class.equals(cl)) {
      return new MBeanInfoDeserializer();
    }
    */
    
    return null;
  }
}
