package com.ydyno.dao;

import com.ydyno.service.dto.ChatInfoDTO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatInfoMgtMapper {
    int insertChatInfo(ChatInfoDTO chatInfoDTO);
}
