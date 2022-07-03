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


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.server.common.data.ResourceUtils;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RuleNode(
        type = ComponentType.TRANSFORMATION,
        name = "parse msg",
        configClazz = TbParseMsgNodeConfiguration.class,
        nodeDescription = "Change Message Originator To Tenant/Customer/Related Entity/Alarm Originator",
        nodeDetails = "Related Entity found using configured relation direction and Relation Type. " +
                "If multiple Related Entities are found, only first Entity is used as new Originator, other entities are discarded.<br/>" +
                "Alarm Originator found only in case original Originator is <code>Alarm</code> entity.",
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbTransformationNodeParseConfig",
        icon = "find_replace"
)
public class TbParseMsgNode extends TbAbstractTransformNode{

    private TbParseMsgNodeConfiguration config;
    private final String modbus_config_path = "mb_config.json";
    private TbModbusMsgWrapper mb;


    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {

        config = new TbParseMsgNodeConfiguration();

        config.data = configuration.getData();

        log.info("get parse msg node: " + config.data.asText());

        try (InputStream inStream = ResourceUtils.getInputStream(this, this.modbus_config_path)) {

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            this.mb = mapper.readValue(inStream, TbModbusMsgWrapper.class);

        }catch (IOException e){
            e.printStackTrace();
        }

    }


    @Override
    protected ListenableFuture<List<TbMsg>> transform(TbContext ctx, TbMsg msg){
        ListenableFuture<String> newMsg = getNewMsg(ctx, msg.getData());
        return Futures.transform(newMsg, n -> {
            if (n == null) {
                return null;
            }

            log.info("converted msg: " + n);

            return Collections.singletonList((ctx.transformMsg(msg, msg.getType(), msg.getOriginator(), msg.getMetaData(), n)));
        }, ctx.getDbCallbackExecutor());


    }



    private ListenableFuture<String> getNewMsg(TbContext ctx, String msg){

        log.info("get origin msg data:{}", msg);

        try {
            JsonNode jm = new ObjectMapper().readTree(msg);

            ExecutorService exec = Executors.newSingleThreadExecutor();
            ListeningExecutorService lexec = MoreExecutors.listeningDecorator(exec);

            return lexec.submit(()->{

                Map<String, List<Float>> data = parse(jm.findValue("rawData"), this.mb);

                Map<String, Float> final_data = new HashMap<String, Float>();

                for(Map.Entry<String, List<Float>>  entry : data.entrySet()){

                    if(entry.getValue().size() > 1){

                        int index = 0;

                        for(Float f : entry.getValue()){
                            final_data.put(entry.getKey() + String.format("_%d", index), f);

                            index++;
                        }
                    }else{
                        final_data.put(entry.getKey(), entry.getValue().get(0));
                    }


                }


                return new ObjectMapper().writeValueAsString(final_data);
            });

        }catch (IOException e){
            e.printStackTrace();
        }


        return Futures.immediateFuture(msg);
    }

    private Map<String, List<Float>> parse(String raw, TbModbusMsgWrapper mb){

        log.info("try to parse raw data: " + raw);

        Map<String, List<Float>> result = new HashMap<String, List<Float>>();

        int index = 0;

        for(TbModbusMsgWrapper.custom c : mb.custom){
            for(TbModbusMsgWrapper.module m : c.Module){
                for(List<TbModbusMsgWrapper.inputReg> ii : m.input_reg){
                    for(TbModbusMsgWrapper.inputReg i : ii){
                        result.put(i.label, convertHexData(raw, index, i.num, i.size, i.type));
                        index += i.num * i.size * 2;
                    }
                }
            }
        }

        return result;
    }


    private Map<String, List<Float>> parse(JsonNode raw, TbModbusMsgWrapper mb){


        log.info("try to parse raw data: {}", raw);

        Map<String, List<Float>> result = new HashMap<String, List<Float>>();

        int index = 0;

        for(TbModbusMsgWrapper.custom c : mb.custom){
            for(TbModbusMsgWrapper.module m : c.Module){
                if(m.input_reg.size() > 0){

                    JsonNode jj = raw.findValue("input."+m.name);

                    String val = "";

                    if(jj != null) {
                        val = raw.findValue("input." + m.name).asText();
                        log.info("parse {} val:{}", ("input."+m.name), val);
                    }else{

                        log.info("can't load item: " + "input." + m.name);

                        continue;
                    }


                    for(List<TbModbusMsgWrapper.inputReg> ii : m.input_reg){
                        for(TbModbusMsgWrapper.inputReg i : ii){

                            if(val != null){

                                result.put(i.label, convertHexData(val, index, i.num, i.size, i.type));
                                index += i.num * i.size * 2;
                            }


                        }
                    }
                }

            }
        }


        return result;
    }


    private  Float doConvert(String data, String type){

        int chu = Integer.parseInt(type.replace("x", ""));

        float res = (float) (Integer.parseInt(data, 16)  / chu);

        return res;
    }

    private  List<Float> convertHexData(String data ,int index, int num, int size, String type){

        if(num > 1){

            List<Float> result = new ArrayList<Float>();

            for(int i = 0; i < num; i++){

                if(index + size * 2 * (i +1) > data.length()){
                    log.error("index outof range of data");
                }else{
                    Float value = doConvert(data.substring(index + size * 2 * i, index + size * 2 * (i+1)), type);
                    result.add(value);
                }
            }

            return result;
        }


        return Collections.singletonList(doConvert(data.substring(index, index + size * 2), type));

    }


    @Override
    public void destroy(){

    }
}
