package com.ecommerce.order_service.controller;


import com.ecommerce.order_service.dto.request.AddToCartRequest;
import com.ecommerce.order_service.dto.request.UpdateCartItemRequest;
import com.ecommerce.order_service.dto.response.CartResponse;
import com.ecommerce.order_service.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartResponse> getCart(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addToCart(
            @Valid @RequestBody AddToCartRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        CartResponse response = cartService.addToCart(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> updateCartItem(
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(cartService.updateCartItem(userId, cartItemId, request));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> removeCartItem(
            @PathVariable Long cartItemId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(cartService.removeCartItem(userId, cartItemId));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}