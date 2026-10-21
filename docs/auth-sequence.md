# Auth Sequence Diagram

This diagram shows the current single-backend auth flow. Spring owns the user record, JWT issuance, and JWT validation.

## Flow Summary

1. `POST /auth/register` creates a new user in PostgreSQL, hashes the password, and returns a JWT.
2. `POST /auth/login` checks the password, then returns a new JWT when credentials are valid.
3. `GET /auth/me` reads the bearer token, resolves the user id, and returns the current user profile.
4. Protected routes like `/orders` and `/accounts` require `Authorization: Bearer <token>`.

## Diagram

```mermaid
sequenceDiagram
	actor Client
	participant API as Spring Backend
	participant UserSvc as UserService
	participant JWT as JwtService
	participant DB as PostgreSQL

	Note over Client,API: Registration and login are public endpoints

	Client->>API: POST /auth/register\n(fullName, email, password)
	API->>UserSvc: createUser(...)
	UserSvc->>DB: Save user with hashed password
	DB-->>UserSvc: Saved user
	UserSvc-->>API: User
	API->>JWT: generateToken(user)
	JWT-->>API: JWT access token
	API-->>Client: 201 Created\n{ user, token }

	Client->>API: POST /auth/login\n(email, password)
	API->>UserSvc: verifyPassword(email, password)
	UserSvc->>DB: Load user by email
	DB-->>UserSvc: User record
	UserSvc-->>API: valid / invalid

	alt Password is valid
		API->>UserSvc: getUserByEmail(email)
		UserSvc->>DB: Load user profile
		DB-->>UserSvc: User
		UserSvc-->>API: User
		API->>JWT: generateToken(user)
		JWT-->>API: JWT access token
		API-->>Client: 200 OK\n{ user, token }
	else Password is invalid
		API-->>Client: 401 Unauthorized
	end

	Client->>API: GET /auth/me\nAuthorization: Bearer <token>
	API->>JWT: parseToken(token)
	JWT-->>API: userId, email, fullName
	API->>UserSvc: getUserById(userId)
	UserSvc->>DB: Load user by id
	DB-->>UserSvc: User
	UserSvc-->>API: User
	API-->>Client: 200 OK\n{ id, fullName, email }

	Client->>API: GET /orders or /accounts\nAuthorization: Bearer <token>
	API->>JWT: parseToken(token)
	JWT-->>API: userId, email, fullName
	API-->>Client: Allow protected request
```