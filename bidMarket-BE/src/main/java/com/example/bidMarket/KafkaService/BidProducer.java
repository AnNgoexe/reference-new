//package com.example.bidMarket.KafkaService;
//
//import com.example.bidMarket.Enum.AuctionStatus;
//import com.example.bidMarket.dto.Request.BidCreateRequest;
//import com.example.bidMarket.exception.AppException;
//import com.example.bidMarket.exception.ErrorCode;
//import com.example.bidMarket.model.Auction;
//import com.example.bidMarket.repository.AuctionRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.stereotype.Service;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class BidProducer {
//    private final AuctionRepository auctionRepository;
//
//    private final KafkaTemplate<String, BidCreateRequest> kafkaTemplate;
//    @Value("${spring.kafka.topic.bid_request}")
//    private String topic_bidRequest;
//
//    public void sendBidRequest(BidCreateRequest bidRequest) {
//        // Sử dụng auctionId làm key
//        String auctionIdKey = bidRequest.getAuctionId().toString();
//
//        Auction auction = auctionRepository.findById(bidRequest.getAuctionId())
//                .orElseThrow(() -> new AppException(ErrorCode.AUCTION_NOT_FOUND));
//        if (auction.getStatus() != AuctionStatus.OPEN) {
//            throw new AppException(ErrorCode.BID_IS_REJECTED);
//        } else {
//            // Tạo ProducerRecord với key là auctionId
//            kafkaTemplate.send(topic_bidRequest, auctionIdKey, bidRequest);
//
//            log.info("Message sent to Kafka with auctionId as key: " + auctionIdKey);
//        }
//
//    }
//}
