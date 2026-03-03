package com.example.my_bot.config;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VkBeans {

    @Bean
    VkApiClient vkApiClient(){
      return new VkApiClient(new HttpTransportClient());
    }

    @Bean
    GroupActor groupActor(@Value("${vk.group.id}") long groupId,
                            @Value("${vk.group.token}") String accessToken){

        return new GroupActor(groupId, accessToken);
    }


}
