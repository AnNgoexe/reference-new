package com.example.bidMarket.service;

import com.example.bidMarket.Enum.AuctionStatus;
import com.example.bidMarket.Enum.CategoryType;
import com.example.bidMarket.dto.Request.AuctionCreateRequest;
import com.example.bidMarket.dto.AuctionDto;
import com.example.bidMarket.dto.Request.AuctionUpdateRequest;
import com.example.bidMarket.dto.Response.AuctionSearchResponse;
import com.example.bidMarket.model.Auction;
import com.example.bidMarket.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuctionService {
    public AuctionDto createAuction (AuctionCreateRequest auctionCreateRequest) throws Exception;
    public Auction updateAuction(UUID id, AuctionUpdateRequest auctionUpdateRequest);
    public void deleteAuction(UUID id);
    public void openAuction(UUID id);
    public void closeAuction(UUID id);
    public void cancelAuction(UUID id);
    public void completeAuction(UUID id);
    public void reOpenAuction(UUID id, AuctionUpdateRequest request);

    public List<AuctionDto> getAllAuction();
    public AuctionSearchResponse getAuctionById(UUID id);

    public Page<Auction> searchAuctions(UUID sellerId,
                                        String title,
                                        List<CategoryType> categoryType,
                                       AuctionStatus status,
                                       BigDecimal minPrice,
                                       BigDecimal maxPrice,
                                       LocalDateTime startTime,
                                       LocalDateTime endTime,
                                       UUID hasNotId,
                                       int page, int size, String sortField, Sort.Direction sortDirection);

    // Synchronize bid count of auction
    public void syncBidCountOfAuction();

    public void updateAuctionStatusOpenToClose();
}
