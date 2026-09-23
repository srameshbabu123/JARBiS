package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.entity.Transaction;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.enums.OrderSide;
import com.jarbis.brokerage.enums.OrderStatus;
import com.jarbis.brokerage.exception.OrderExecutionException;
import com.jarbis.brokerage.exception.OrderNotFoundException;
import com.jarbis.brokerage.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for Order management.
 * Handles order lifecycle, validation, and status transitions.
 */
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserService userService;
    private final AccountService accountService;

    @Autowired
    public OrderService(OrderRepository orderRepository, UserService userService,
                       AccountService accountService) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.accountService = accountService;
    }

    // ==================== Order Creation ====================

    /**
     * Create a new order for a user.
     *
     * @param asset the asset being traded
     * @param owner the user placing the order
     * @param status the initial order status
     * @param side the order side (BUY or SELL)
     * @param quantity the quantity to trade
     * @param price the price per unit
     * @return the created order
     */
    public Order createOrder(Asset asset, User owner, OrderStatus status, 
                            com.jarbis.brokerage.enums.OrderSide side, 
                            Double quantity, Double price) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset cannot be null");
        }
        if (owner == null) {
            throw new IllegalArgumentException("Owner cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        if (side == null) {
            throw new IllegalArgumentException("Order side cannot be null");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
        if (price == null || price <= 0) {
            throw new IllegalArgumentException("Price must be greater than 0");
        }

        userService.verifyUserExists(owner.getId());

        Order order = new Order(asset, owner, status);
        order.setSide(side);
        order.setQuantity(quantity);
        order.setPrice(price);
        
        return orderRepository.save(order);
    }

    /**
     * Create a new PENDING order.
     *
     * @param asset the asset being traded
     * @param owner the user placing the order
     * @param side the order side (BUY or SELL)
     * @param quantity the quantity to trade
     * @param price the price per unit
     * @return the created order
     */
    public Order createPendingOrder(Asset asset, User owner, 
                                    OrderSide side, Double quantity, Double price) {
        return createOrder(asset, owner, OrderStatus.PENDING, side, quantity, price);
    }

    // ==================== Order Retrieval ====================

    /**
     * Get order by ID.
     *
     * @param orderId the order ID
     * @return the order
     * @throws OrderNotFoundException if order not found
     */
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(
                        "Order not found with id: " + orderId));
    }

    /**
     * Get all orders.
     *
     * @return list of all orders
     */
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    /**
     * Get all orders for a specific user.
     *
     * @param userId the user ID
     * @return list of user's orders
     */
    public List<Order> getOrdersByUser(Long userId) {
        userService.verifyUserExists(userId);
        return orderRepository.findByOwnerId(userId);
    }

    /**
     * Get all orders with a specific status.
     *
     * @param status the order status
     * @return list of orders with the given status
     */
    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    /**
     * Get all PENDING orders.
     *
     * @return list of pending orders
     */
    public List<Order> getPendingOrders() {
        return getOrdersByStatus(OrderStatus.PENDING);
    }

    /**
     * Get all COMPLETED orders.
     *
     * @return list of completed orders
     */
    public List<Order> getCompletedOrders() {
        return getOrdersByStatus(OrderStatus.COMPLETED);
    }

    /**
     * Get all CANCELLED orders.
     *
     * @return list of cancelled orders
     */
    public List<Order> getCancelledOrders() {
        return getOrdersByStatus(OrderStatus.CANCELLED);
    }

    /**
     * Get orders for a specific user filtered by status.
     *
     * @param userId the user ID
     * @param status the order status
     * @return list of user's orders with the given status
     */
    public List<Order> getOrdersByUserAndStatus(Long userId, OrderStatus status) {
        userService.verifyUserExists(userId);
        return orderRepository.findByOwnerIdAndStatus(userId, status);
    }

    /**
     * Get all orders for a specific asset.
     *
     * @param assetId the asset ID
     * @return list of orders for the asset
     */
    public List<Order> getOrdersByAsset(Long assetId) {
        return orderRepository.findByAssetId(assetId);
    }

    /**
     * Get all orders in a specific transaction.
     *
     * @param transactionId the transaction ID
     * @return list of orders in the transaction
     */
    public List<Order> getOrdersByTransaction(Long transactionId) {
        return orderRepository.findByTransactionId(transactionId);
    }

    // ==================== Order Status Management ====================

    /**
     * Update the status of an order.
     *
     * @param orderId the order ID
     * @param newStatus the new order status
     * @return the updated order
     */
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }

        Order order = getOrderById(orderId);
        validateStatusTransition(order.getStatus(), newStatus);
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    /**
     * Complete an order (PENDING → COMPLETED).
     *
     * @param orderId the order ID
     * @return the updated order
     */
    public Order completeOrder(Long orderId) {
        return updateOrderStatus(orderId, OrderStatus.COMPLETED);
    }

    /**
     * Cancel an order (PENDING → CANCELLED).
     *
     * @param orderId the order ID
     * @return the updated order
     */
    public Order cancelOrder(Long orderId) {
        Order order = getOrderById(orderId);
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot cancel a completed order");
        }
        return updateOrderStatus(orderId, OrderStatus.CANCELLED);
    }

    /**
     * Validate that a status transition is allowed.
     * PENDING can transition to COMPLETED or CANCELLED
     * COMPLETED and CANCELLED are terminal states
     *
     * @param currentStatus the current order status
     * @param newStatus the proposed new status
     */
    private void validateStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        if (currentStatus == OrderStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot transition from COMPLETED status");
        }
        if (currentStatus == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot transition from CANCELLED status");
        }
        if (currentStatus == newStatus) {
            throw new IllegalStateException(
                    "Order is already in " + newStatus + " status");
        }
    }

    // ==================== Order Validation ====================

    /**
     * Check if an order is in a terminal state (COMPLETED or CANCELLED).
     *
     * @param order the order
     * @return true if order is terminal
     */
    public boolean isOrderTerminal(Order order) {
        return order.getStatus() == OrderStatus.COMPLETED 
                || order.getStatus() == OrderStatus.CANCELLED;
    }

    /**
     * Check if an order can be executed (still PENDING).
     *
     * @param order the order
     * @return true if order can be executed
     */
    public boolean isOrderExecutable(Order order) {
        return order.getStatus() == OrderStatus.PENDING;
    }

    /**
     * Check if an order can be cancelled (still PENDING).
     *
     * @param order the order
     * @return true if order can be cancelled
     */
    public boolean canOrderBeCancelled(Order order) {
        return order.getStatus() == OrderStatus.PENDING;
    }

    /**
     * Check if an order belongs to a specific user.
     *
     * @param orderId the order ID
     * @param userId the user ID
     * @return true if order belongs to user
     */
    public boolean orderBelongsToUser(Long orderId, Long userId) {
        Order order = getOrderById(orderId);
        return order.getOwner().getId().equals(userId);
    }

    // ==================== Order Management ====================

    /**
     * Associate an order with a transaction.
     *
     * @param orderId the order ID
     * @param transaction the transaction
     * @return the updated order
     */
    public Order assignOrderToTransaction(Long orderId, Transaction transaction) {
        Order order = getOrderById(orderId);
        order.setTransaction(transaction);
        return orderRepository.save(order);
    }

    /**
     * Remove an order from its transaction.
     *
     * @param orderId the order ID
     * @return the updated order
     */
    public Order removeOrderFromTransaction(Long orderId) {
        Order order = getOrderById(orderId);
        order.setTransaction(null);
        return orderRepository.save(order);
    }

    /**
     * Delete an order. Only PENDING or CANCELLED orders can be deleted.
     *
     * @param orderId the order ID
     */
    public void deleteOrder(Long orderId) {
        Order order = getOrderById(orderId);
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot delete a completed order");
        }
        orderRepository.delete(order);
    }

    /**
     * Get count of orders for a user.
     *
     * @param userId the user ID
     * @return the count of orders
     */
    public Long getOrderCountByUser(Long userId) {
        return (long) getOrdersByUser(userId).size();
    }

    /**
     * Get count of pending orders for a user.
     *
     * @param userId the user ID
     * @return the count of pending orders
     */
    public Long getPendingOrderCountByUser(Long userId) {
        return (long) getOrdersByUserAndStatus(userId, OrderStatus.PENDING).size();
    }

    // ==================== Order Execution ====================

    /**
     * Execute an individual order based on its side (BUY or SELL).
     * For BUY orders: deducts funds from account and creates/updates holding.
     * For SELL orders: reduces holding and credits funds to account.
     *
     * @param orderId the order ID
     * @throws OrderNotFoundException if order not found
     * @throws OrderExecutionException if order cannot be executed
     */
    public void executeOrder(Long orderId) {
        Order order = getOrderById(orderId);

        if (!isOrderExecutable(order)) {
            throw new OrderExecutionException("Order is not in PENDING status and cannot be executed");
        }

        Long accountId = order.getOwner().getId();
        Asset asset = order.getAsset();
        Double quantity = order.getQuantity();
        Double price = order.getPrice();

        if (order.getSide() == OrderSide.BUY) {
            accountService.buyAsset(accountId, asset, quantity, price);
        } else if (order.getSide() == OrderSide.SELL) {
            accountService.sellAsset(accountId, asset, quantity, price);
        } else {
            throw new OrderExecutionException("Unknown order side: " + order.getSide());
        }

        completeOrder(orderId);
    }
}
