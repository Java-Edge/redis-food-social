package com.javaedge.oauth2.server.controller;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.javaedge.commons.model.domain.ResultInfo;
import com.javaedge.oauth2.server.OAuth2ServerApplicationTests;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.Base64Utils;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class OAuthControllerTest extends OAuth2ServerApplicationTests {

    @Test
    public void writeToken() throws Exception {
        String authorization = Base64Utils.encodeToString("appId:123456".getBytes());
        StringBuilder tokens = new StringBuilder();
        // 生成 2000 个用户的 token
        for (int i = 0; i < 2000; i++) {
            MvcResult mvcResult = super.mockMvc.perform(MockMvcRequestBuilders.post("/oauth/token")
                    .header("Authorization", "Basic " + authorization)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("username", "test" + i)
                    .param("password", "123456")
                    .param("grant_type", "password")
                    .param("scope", "api")
            )
                    .andExpect(status().isOk())
                    // .andDo(print())
                    .andReturn();
            String contentAsString = mvcResult.getResponse().getContentAsString();
            ResultInfo resultInfo = JSONUtil.toBean(contentAsString, ResultInfo.class);
            JSONObject result = (JSONObject) resultInfo.getData();
            String token = result.getStr("accessToken");
            tokens.append(token).append("\r\n");
        }
        Files.write(Paths.get("tokens.txt"), tokens.toString().getBytes());
    }

}