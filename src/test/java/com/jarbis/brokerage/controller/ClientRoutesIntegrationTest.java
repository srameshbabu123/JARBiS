package com.jarbis.brokerage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarbis.brokerage.config.SecurityConfig;
import com.jarbis.brokerage.entity.*;
import com.jarbis.brokerage.enums.*;
import com.jarbis.brokerage.repository.*;
import com.jarbis.brokerage.service.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring routes, JWT filter, method security and JSON; business dependencies are mocked. */
@WebMvcTest({AuthController.class, AccountController.class, UserController.class,
    HoldingController.class, OrderController.class, AssetController.class, MarketDataController.class})
@Import({SecurityConfig.class, JwtService.class, ClientAccess.class})
class ClientRoutesIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @MockBean UserService users;
    @MockBean AccountService accounts;
    @MockBean OrderService orders;
    @MockBean AssetService assets;
    @MockBean HoldingService holdings;
    @MockBean HoldingRepository holdingRepository;
    @MockBean AssetRepository assetRepository;
    @MockBean AccountRepository accountRepository;
    @MockBean RedisMarketDataHistoryService market;
    private User user;
    private Account account;
    private Asset asset;
    private String bearer;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> UUID.randomUUID().toString() + UUID.randomUUID());
    }

    @BeforeEach void setUp() throws Exception {
        // Negative IDs mirror the shared baseline and must work through the UI contracts.
        user = new User("Client Test", "client@example.test", "not-returned");
        user.setId(-1001L);
        account = mock(Account.class);
        when(account.getId()).thenReturn(-1101L);
        when(account.getOwner()).thenReturn(user);
        when(account.getBalance()).thenReturn(1000.0);
        when(account.getAccountType()).thenReturn(AccountType.INVESTMENT);
        when(account.getCurrency()).thenReturn(Currency.USD);
        asset = new StockAsset("AAPL");
        var id = Asset.class.getDeclaredField("id");
        id.setAccessible(true); id.set(asset, -2001L);
        bearer = "Bearer " + jwt.generateToken(user);
        when(users.getUserById(-1001L)).thenReturn(user);
        when(accounts.getAccountsByUser(-1001L)).thenReturn(List.of(account));
        when(accounts.initializeMyAccount(-1001L)).thenReturn(account);
        when(accounts.getAccountByUser(-1001L, -1101L)).thenReturn(account);
        when(accounts.getAccountById(-1101L)).thenReturn(account);
        when(accountRepository.findById(-1101L)).thenReturn(Optional.of(account));
        when(holdingRepository.findByAccountId(-1101L)).thenReturn(List.of());
        when(orders.getOrdersByAccount(-1101L)).thenReturn(List.of());
        when(assetRepository.findAll()).thenReturn(List.of(asset));
        when(assetRepository.findByNameIgnoreCase("AAPL")).thenReturn(Optional.of(asset));
    }

    @Test void clientReadRoutesRequireJwtAndReturnExpectedShapes() throws Exception {
        mvc.perform(get("/accounts/mine")).andExpect(status().isUnauthorized());
        mvc.perform(post("/accounts/mine/initialize")).andExpect(status().isUnauthorized());
        mvc.perform(post("/accounts/mine/initialize").header("Authorization", bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(-1001));
        mvc.perform(get("/auth/me").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me").header("Authorization", bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(-1001));
        mvc.perform(get("/users/-1001").header("Authorization", bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/accounts/mine").header("Authorization", bearer)).andExpect(status().isOk()).andExpect(jsonPath("$[0].ownerId").value(-1001));
        mvc.perform(get("/accounts/mine/-1101").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/holdings/account/-1101").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/orders/account/-1101").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/assets").header("Authorization", bearer)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(-2001));
        mvc.perform(get("/assets/by-symbol/AAPL").header("Authorization", bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("AAPL"));
        mvc.perform(get("/assets/by-symbol/UNKNOWN").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(get("/market-data/AAPL/latest").header("Authorization", bearer)).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/market-data/AAPL/history").param("from", "2026-10-01T00:00:00Z").param("to", "2026-10-20T00:00:00Z").header("Authorization", bearer)).andExpect(status().isOk());
    }

    @Test void loginRegistrationAndMutationsUseJavaContracts() throws Exception {
        String password = UUID.randomUUID().toString();
        when(users.verifyPassword(user.getEmail(), password)).thenReturn(true);
        when(users.getUserByEmail(user.getEmail())).thenReturn(user);
        when(users.createUser(eq(user.getFullName()), eq(user.getEmail()), anyString())).thenReturn(user);
        mvc.perform(post("/auth/login").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("email", user.getEmail(), "password", password))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
        mvc.perform(post("/auth/login").contentType("application/json").content("{\"email\":\"missing@example.test\",\"password\":\"invalid\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/register").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("email", user.getEmail(), "fullName", user.getFullName(), "password", password))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.user.id").value(-1001));
        mvc.perform(post("/auth/register").contentType("application/json").content("{}")) .andExpect(status().isBadRequest());
        when(accounts.createAccount(-1001L, AccountType.INVESTMENT, Currency.USD)).thenReturn(account);
        mvc.perform(post("/accounts").header("Authorization", bearer).contentType("application/json").content("{\"userId\":-1001,\"accountType\":\"INVESTMENT\",\"currency\":\"USD\"}"))
            .andExpect(status().isCreated());
        when(accounts.changeCash(eq(-1001L), eq(-1101L), eq(25.0), anyBoolean())).thenReturn(account);
        for (String action : List.of("deposit", "withdraw")) {
            mvc.perform(post("/accounts/-1101/" + action).header("Authorization", bearer).contentType("application/json").content("{\"amount\":25}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(1000));
        }
        when(users.updateUser(-1001L, "Updated", user.getEmail())).thenReturn(user);
        mvc.perform(put("/users/-1001").header("Authorization", bearer).contentType("application/json").content("{\"fullName\":\"Updated\",\"email\":\"client@example.test\"}"))
            .andExpect(status().isOk());
        when(assets.getAssetById(-2001L)).thenReturn(asset);
        Order order = new Order(asset, account, OrderStatus.PENDING);
        order.setSide(OrderSide.BUY); order.setQuantity(2.0); order.setPrice(100.0);
        when(orders.createPendingOrder(asset, account, OrderSide.BUY, 2.0, 100.0)).thenReturn(order);
        mvc.perform(post("/orders").header("Authorization", bearer).contentType("application/json").content("{\"accountId\":-1101,\"assetId\":-2001,\"side\":\"BUY\",\"quantity\":2,\"price\":100}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.asset.name").value("AAPL"))
            .andExpect(jsonPath("$.remainingQuantity").value(2)).andExpect(jsonPath("$.account").doesNotExist());
    }

    @Test void anotherUsersIdsCannotBeUsedByTheClient() throws Exception {
        mvc.perform(get("/users/99").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/orders/account/99").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/holdings/account/99").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(post("/accounts").header("Authorization", bearer).contentType("application/json").content("{\"userId\":99,\"accountType\":\"INVESTMENT\",\"currency\":\"USD\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/orders").header("Authorization", bearer).contentType("application/json").content("{\"accountId\":99,\"assetId\":-2001,\"side\":\"BUY\",\"quantity\":1,\"price\":100}"))
            .andExpect(status().isForbidden());
    }
}
