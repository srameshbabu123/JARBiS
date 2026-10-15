package com.jarbis.brokerage.repository;

import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
	List<Order> findByStatus(OrderStatus status);
	List<Order> findByAccountOwnerId(Long userId);
	List<Order> findByAccountOwnerIdAndStatus(Long userId, OrderStatus status);
	List<Order> findByAccountId(Long accountId);
	List<Order> findByAssetId(Long assetId);
	List<Order> findByAssetIdAndStatusIn(Long assetId, Collection<OrderStatus> statuses);

	@Query("""
			SELECT o FROM Order o
			WHERE EXISTS (
				SELECT 1 FROM Transaction t
				WHERE t.id = :transactionId
				AND (t.buyOrder = o OR t.sellOrder = o)
			)
			""")
	List<Order> findByTransactionId(Long transactionId);
}
