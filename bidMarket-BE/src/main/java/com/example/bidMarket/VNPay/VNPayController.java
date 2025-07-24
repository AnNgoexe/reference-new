package com.example.bidMarket.VNPay;


import com.example.bidMarket.Enum.OrderStatus;
import com.example.bidMarket.Enum.PaymentMethod;
import com.example.bidMarket.Enum.PaymentStatus;
import com.example.bidMarket.dto.PaymentDto;
import com.example.bidMarket.dto.Request.ShippingCreateRequest;
import com.example.bidMarket.exception.AppException;
import com.example.bidMarket.exception.ErrorCode;
import com.example.bidMarket.mapper.PaymentMapper;
import com.example.bidMarket.model.Order;
import com.example.bidMarket.notification.CreateNotificationRequest;
import com.example.bidMarket.notification.NotificationService;
import com.example.bidMarket.repository.OrderRepository;
import com.example.bidMarket.service.OrderService;
import com.example.bidMarket.service.PaymentService;
import com.example.bidMarket.service.ShippingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jdk.jfr.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/VNPay")
@RequiredArgsConstructor
@Slf4j
public class VNPayController {
    private final OrderRepository orderRepository;

    private final VNPayService vnPayService;
    private final PaymentService paymentService;
    private final OrderService orderService;
    private final ShippingService shippingService;
    private final NotificationService notificationService;

    private final PaymentMapper paymentMapper;

    /*
    - Chuyển hướng người dùng đến cổng thanh toán VNPAY
    - order info is order id
    */
    @PostMapping("/submitOrder")
    public String submidOrder(@RequestParam("orderInfo") String orderInfo,  // Order info is order id
                              HttpServletRequest request){
        Order order = orderRepository.findById(UUID.fromString(orderInfo))
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.error("Order is not pending so you can't payment");
            throw new AppException(ErrorCode.PAYMENT_FAILED);
        }
        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        String vnpayUrl = vnPayService.createPayment(request, orderInfo, baseUrl);
        return "redirect:" + vnpayUrl;
    }

    // Sau khi hoàn tất thanh toán, VNPAY sẽ chuyển hướng trình duyệt về URL này
    @GetMapping("/vnpay-payment-return")
    public void paymentCompleted(HttpServletRequest request, Model model, HttpServletResponse response) {
        PaymentDto paymentDto = paymentService.processPayment(request);
        CreateNotificationRequest notificationRequest;

        // Cập nhật trạng thái đơn hàng nếu thanh toán thành công
        if (paymentDto.getStatus() == PaymentStatus.SUCCESS) {
            orderService.updateStatus(paymentDto.getOrderId(), OrderStatus.PAID);
            Order order = orderRepository.findById(paymentDto.getOrderId()).orElseThrow(() -> new RuntimeException("Order not found"));
            shippingService.createShipping(ShippingCreateRequest.builder()
                            .auctionId(order.getAuction().getId())
                            .sellerId(order.getAuction().getProduct().getSeller().getId())
                            .buyerId(order.getUser().getId())
                            .price(order.getTotalAmount())
                    .build());
            notificationRequest = CreateNotificationRequest.builder()
                    .userId(paymentDto.getUserId())
                    .message("Your payment has been successfully processed.")
                    .build();
        } else {
            notificationRequest = CreateNotificationRequest.builder()
                    .userId(paymentDto.getUserId())
                    .message("Your payment has been failed.")
                    .build();
        }

        model.addAttribute("orderId", paymentDto.getOrderId());
        model.addAttribute("totalPrice", paymentDto.getAmount());
        model.addAttribute("paymentTime", paymentDto.getPaymentDate());
        model.addAttribute("transactionId", paymentDto.getTransactionId());

        notificationService.createNotification(notificationRequest);

        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        String orderUrl = baseUrl + "/order";

        try {
            response.sendRedirect(orderUrl);
        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
        }
    }

    @GetMapping("/vnpay-test")
    public void test(HttpServletResponse response) {
//        return """
//            <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; font-family: Arial, sans-serif; text-align: center;">
//                <h1 style="color: #4CAF50; font-size: 2em; margin-bottom: 20px;">Payment Completed</h1>
//                <p style="font-size: 1.2em; margin-bottom: 30px;">Thank you for your payment. Your transaction has been successfully processed.</p>
//                <a href="http://fall2024c8g9.int3306.freeddns.org/order"
//                   style="text-decoration: none; padding: 10px 20px; background-color: #4CAF50; color: white; border-radius: 5px; font-size: 1em;">
//                    Back to Order
//                </a>
//            </div>
//        """;
        try {
            response.sendRedirect("http://fall2024c8g9.int3306.freeddns.org/order");
        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
        }
    }

}
