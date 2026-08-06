package com.igorservice.orderservice.repository;

import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByCustomerId(String customerId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate")
    Page<Order> findByCreatedAtBetween(
        @Param("startDate") java.time.Instant startDate,
        @Param("endDate") java.time.Instant endDate,
        Pageable pageable
    );

    long countByStatus(OrderStatus status);

    @Modifying
    @Query("UPDATE Order o SET o.createdAt = :createdAt WHERE o.id = :id")
    void updateCreatedAt(@Param("id") UUID id, @Param("createdAt") java.time.Instant createdAt);
}
