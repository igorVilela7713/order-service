package com.igorservice.orderservice.service;

import com.igorservice.orderservice.dto.OrderResponse;
import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderStatus;
import com.igorservice.orderservice.repository.OrderRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderSearchService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Page<OrderResponse> search(
            Instant startDate,
            Instant endDate,
            OrderStatus status,
            String customerId,
            Pageable pageable) {

        log.debug("Searching orders — startDate: {}, endDate: {}, status: {}, customerId: {}",
                startDate, endDate, status, customerId);

        Specification<Order> spec = buildSpecification(startDate, endDate, status, customerId);
        return orderRepository.findAll(spec, pageable).map(OrderResponse::fromEntity);
    }

    private Specification<Order> buildSpecification(
            Instant startDate,
            Instant endDate,
            OrderStatus status,
            String customerId) {

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (startDate != null && endDate != null) {
                predicates.add(criteriaBuilder.between(root.get("createdAt"), startDate, endDate));
            } else if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            } else if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (customerId != null && !customerId.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("customerId"), customerId));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
