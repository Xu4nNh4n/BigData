package com.api.supermarket.service;

import com.api.supermarket.dto.response.TokenResponse;
import com.api.supermarket.entity.User;

public interface RefreshTokenService {
    String createRefreshToken(User user); 
  //  createRefreshToken()
  // -> tạo chuỗi ngẫu nhiên
  // -> hash token
  // -> lưu database
  // -> trả token gốc cho client

    TokenResponse refresh(String refreshToken);
    // refresh()
    //   -> hash token client gửi
    //   -> tìm trong database
    //   -> kiểm tra hết hạn
    //   -> kiểm tra revoked
    //   -> kiểm tra User còn active
    //   -> tạo Access Token mới

    void revoke(String refreshToken);
    // revoke()
    //   -> tìm token
    //   -> revoked = true
    //   -> revokedAt = thời điểm hiện tại



   
}
