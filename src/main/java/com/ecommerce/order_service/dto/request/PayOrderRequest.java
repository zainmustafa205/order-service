package com.ecommerce.order_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PayOrderRequest {

    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "CARD|WALLET", message = "Payment method must be CARD or WALLET")
    private String paymentMethod;
}