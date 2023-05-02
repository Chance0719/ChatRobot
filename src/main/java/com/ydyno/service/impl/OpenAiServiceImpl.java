/*
 *  Copyright 2019-2020 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.ydyno.service.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.text.UnicodeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.ydyno.config.OpenAiConfig;
import com.ydyno.dao.ChatInfoMgtMapper;
import com.ydyno.service.WebSocketServer;
import com.ydyno.service.dto.ChatInfoDTO;
import com.ydyno.service.dto.OpenAiRequest;
import com.ydyno.service.OpenAiService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Zheng Jie
 * @description OpenAi接口实现类
 * @date 2023-02-15
 **/
@Slf4j
@Service
@AllArgsConstructor
public class OpenAiServiceImpl implements OpenAiService {

    @Resource
    private ChatInfoMgtMapper chatInfoMgtDAO;

    private final OpenAiConfig openAiConfig;

    @Override
    public void communicate(OpenAiRequest openAiDto, WebSocketServer webSocketServer) throws Exception {
        // 获取apikey
        String apikey = openAiDto.getApikey();
        // 最大返回字符数, max_tokens不能超过模型的上下文长度。大多数模型的上下文长度为 2048 个标记
        int maxTokens = 2048;
        // 如果没有传入apikey，则使用配置文件中的
        if(StrUtil.isBlank(apikey)){
            apikey = openAiConfig.getApiKey();
            maxTokens = openAiConfig.getMaxTokens();
        }
        // 根据id判断调用哪个接口
        try {
            switch (openAiDto.getId()){
                // 文本问答
                case 1:
                    textQuiz(maxTokens, openAiDto, apikey, webSocketServer);
                    break;
                // 图片生成
                case 2:
                    imageQuiz(openAiDto, apikey, webSocketServer);
                    break;
                // 默认
                default:
                    webSocketServer.sendMessage("出错了：未知的请求类型");
            }
        } catch (Exception e){
            e.printStackTrace();
            webSocketServer.sendMessage("出错了：" + e.getMessage());
        }
    }

    /**
     * 文本问答
     *
     * @param maxTokens       最大字符数
     * @param openAiRequest       请求参数
     * @param apikey          apikey
     * @param webSocketServer /
     */
    private void textQuiz(Integer maxTokens, OpenAiRequest openAiRequest, String apikey, WebSocketServer webSocketServer) throws Exception {
        ChatInfoDTO chatInfoDTO = new ChatInfoDTO();
        chatInfoDTO.setSid(webSocketServer.getSid());
        chatInfoDTO.setApikey(apikey);
        chatInfoDTO.setModel(openAiConfig.getModel());
        // 构建对话参数
        List<Map<String, String>> messages = new ArrayList<>();
        // 如果是连续对话，逐条添加对话内容
        if(openAiRequest.getKeep() == 1){
            String[] keepTexts = openAiRequest.getKeepText().split("\n");
            for(String keepText : keepTexts){
                String[] split = keepText.split("・・");
                for(String str : split){
                    String[] data = str.split(":");
                    if(data.length < 2){
                        continue;
                    }
                    String role = data[0];
                    String content = data[1];
                    Map<String, String> userMessage = MapUtil.ofEntries(
                            MapUtil.entry("role", role),
                            MapUtil.entry("content", content)
                    );
                    messages.add(userMessage);
                }
            }
        } else {
            Map<String, String> userMessage = MapUtil.ofEntries(
                    MapUtil.entry("role", "user"),
                    MapUtil.entry("content", openAiRequest.getText())
            );
            messages.add(userMessage);
        }
        chatInfoDTO.setMessage(messages.toString());
        // 构建请求参数
        Map<String, Object> params = MapUtil.ofEntries(
                MapUtil.entry("stream", true),
                MapUtil.entry("max_tokens", maxTokens),
                MapUtil.entry("model", openAiConfig.getModel()),
                MapUtil.entry("temperature", openAiConfig.getTemperature()),
                MapUtil.entry("messages", messages)
        );
        // 调用接口
        HttpResponse result;
        try {
            result = HttpRequest.post(openAiConfig.getOpenaiApi())
                    .header(Header.CONTENT_TYPE, "application/json")
                    .header(Header.AUTHORIZATION, "Bearer " + apikey)
                    .body(JSONUtil.toJsonStr(params))
                    .executeAsync();
        }catch (Exception e){
            e.printStackTrace();
            webSocketServer.sendMessage("出错了：" + e.getMessage());
            return;
        }
        // 处理数据
        String line;
        BufferedReader reader = new BufferedReader(new InputStreamReader(result.bodyStream()));
        boolean flag = false;
        boolean printErrorMsg = false;
        StringBuilder errMsg = new StringBuilder();
        StringBuilder msg = new StringBuilder();
        while((line = reader.readLine()) != null){
            String msgResult = UnicodeUtil.toString(line);
            // 正则匹配错误信息
            if(msgResult.contains("\"error\":")){
                printErrorMsg = true;
            }
            // 如果出错，打印错误信息
            if (printErrorMsg) {
                log.error(msgResult);
                errMsg.append(msgResult);
            }
            // 正则匹配结果
            Matcher m = Pattern.compile("\"content\":\"(.*?)\"").matcher(msgResult);
            if(m.find()) {
                // 将\n和\t替换为html中的换行和制表，将\替换为"
                String data = m.group(1).replace("\\n", "\n")
                        .replace("\\t", "\t")
                        .replace("\\", "\"");
                // 过滤AI回复开头的换行
                if(!data.matches("\\n+") && !flag) {
                    flag = true;
                }
                // 发送信息
                if(flag) {
                    msg.append(data);
                    webSocketServer.sendMessage(data);
                }
            }
        }
        chatInfoDTO.setAnswer(msg.toString());
        chatInfoMgtDAO.insertChatInfo(chatInfoDTO);
        // 关闭流
        reader.close();
        // 如果出错，抛出异常
        if (printErrorMsg){
            Matcher m = Pattern.compile("\"message\": \"(.*?)\"").matcher(errMsg.toString());
            if (m.find()){
                throw new RuntimeException(m.group(1));
            }
            throw new RuntimeException("请求ChatGPT官方服务出错，请稍后再试");
        };
    }

    /**
     * 图片请求
     *
     * @param openAiDto       请求参数
     * @param apikey          apiKey
     * @param webSocketServer /
     */
    private void imageQuiz(OpenAiRequest openAiDto, String apikey, WebSocketServer webSocketServer) throws IOException {
        ChatInfoDTO chatInfoDTO = new ChatInfoDTO();
        chatInfoDTO.setSid(webSocketServer.getSid());
        chatInfoDTO.setApikey(apikey);
        chatInfoDTO.setMessage(openAiDto.getText());
        // 请求参数
        Map<String, Object> params = MapUtil.ofEntries(
                MapUtil.entry("prompt", openAiDto.getText()),
                MapUtil.entry("size", "256x256")
        );
        // 调用接口
        String result = HttpRequest.post(openAiConfig.getImageApi())
                .header(Header.CONTENT_TYPE, "application/json")
                .header(Header.AUTHORIZATION, "Bearer " + apikey)
                .body(JSONUtil.toJsonStr(params))
                .execute().body();
        // 正则匹配出结果
        Pattern p = Pattern.compile("\"url\": \"(.*?)\"");
        Matcher m = p.matcher(result);
        if (m.find()){
            String url = m.group(1);
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setReadTimeout(5000);
                connection.setConnectTimeout(5000);
                connection.setRequestMethod("GET");
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    byte[] bytes = IOUtils.toByteArray(inputStream);
                    chatInfoDTO.setImage(bytes);
                }
            } catch (IOException e) {
                log.info("获取网络图片出现异常，图片路径为：" + url);
            }
            chatInfoMgtDAO.insertChatInfo(chatInfoDTO);
            webSocketServer.sendMessage(url);
        } else {
            webSocketServer.sendMessage("图片生成失败！");
        }
    }
}
