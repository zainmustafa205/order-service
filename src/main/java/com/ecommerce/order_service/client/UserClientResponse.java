package com.ecommerce.order_service.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserClientResponse {
    private Long id;
    private String username;
    private String email;
}