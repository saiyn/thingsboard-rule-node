/**
 * Copyright © 2018 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.rule.engine.node.transform;

import java.util.List;
import java.util.Map;

public class TbModbusMsgWrapper {

    public String version;

    public List<custom> custom;


    public static class custom{
        public String  cid;
        public List<module> Module;
    }

    public static class module{
        public String  name;
        public List<List<inputReg>> input_reg;
    }

    public static class inputReg{
        public String  label;
        public int     num;
        public String  type;
        public int     size;
    }


}
