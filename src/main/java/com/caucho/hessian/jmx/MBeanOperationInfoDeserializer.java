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

import com.caucho.hessian.io.AbstractDeserializer;
import com.caucho.hessian.io.AbstractHessianInput;

import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import java.io.IOException;

/**
 * Deserializing an MBeanOperationInfo valued object
 */
public class MBeanOperationInfoDeserializer extends AbstractDeserializer {
  public Class getType()
  {
    return MBeanOperationInfo.class;
  }
  
  public Object readMap(AbstractHessianInput in)
    throws IOException
  {
    String name = null;
    String type = null;
    String description = null;
    MBeanParameterInfo []sig = null;
    int impact = 0;
    
    while (! in.isEnd()) {
      String key = in.readString();

      if ("name".equals(key))
        name = in.readString();
      else if ("description".equals(key))
        description = in.readString();
      else if ("type".equals(key))
        type = in.readString();
      else if ("impact".equals(key))
        impact = in.readInt();
      else if ("signature".equals(key)) {
        sig = (MBeanParameterInfo[]) in.readObject(MBeanParameterInfo[].class);
      }
      else {
        in.readObject();
      }
    }

    in.readMapEnd();

    try {
      MBeanOperationInfo info;

      info = new MBeanOperationInfo(name, description, sig, type, impact);
      
      return info;
    } catch (Exception e) {
      throw new IOException(String.valueOf(e));
    }
  }
}
