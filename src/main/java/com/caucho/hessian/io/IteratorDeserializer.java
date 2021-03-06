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
import java.util.ArrayList;

/**
 * Deserializing a JDK 1.2 Collection.
 */
public class IteratorDeserializer extends AbstractListDeserializer {
  private static IteratorDeserializer _deserializer;

  public static IteratorDeserializer create()
  {
    if (_deserializer == null)
      _deserializer = new IteratorDeserializer();

    return _deserializer;
  }
  
  @Override
  public Object readList(AbstractHessianInput in, int length)
    throws IOException
  {
    ArrayList list = new ArrayList();

    in.addRef(list);

    while (! in.isEnd())
      list.add(in.readObject());

    in.readEnd();

    return list.iterator();
  }
}


