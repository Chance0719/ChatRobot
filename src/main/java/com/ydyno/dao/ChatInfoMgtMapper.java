package com.ydyno.dao;

import com.ydyno.service.dto.ApiKeyDTO;
import com.ydyno.service.dto.ChatInfoDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatInfoMgtMapper {
    int insertChatInfo(ChatInfoDTO chatInfoDTO);

    List<String> queryApiKeysBySid(String sid);

    List<String> queryApiKeysByApiKey(String apikey);

    void insertApikey(ApiKeyDTO apiKeyDTO);

    void updateApiKeysInfo(ApiKeyDTO apiKeyDTO);
}
