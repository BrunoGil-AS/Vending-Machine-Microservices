
# API Gateway Authentication and User Management

This collection provides a set of requests to test the authentication and user management functionalities of the API Gateway.

## Authentication

### 1. Login

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/auth/login`
- **Headers:**
  - `Content-Type: application/json`
- **Body (raw, JSON):**
  ```json
  {
    "username": "admin",
    "password": "password"
  }
  ```

## User Management (Admin Only)

**Note:** These endpoints require a valid JWT token from the login request in the `Authorization` header.

- **Header:** `Authorization: Bearer <YOUR_JWT_TOKEN>`

### 2. Create User

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/auth/users`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer <YOUR_JWT_TOKEN>`
- **Body (raw, JSON):**
  ```json
  {
    "username": "newuser",
    "password": "password",
    "role": "USER"
  }
  ```

### 3. Get All Users

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/auth/users`
- **Headers:**
  - `Authorization: Bearer <YOUR_JWT_TOKEN>`

### 4. Get User by ID

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/auth/users/1`
- **Headers:**
  - `Authorization: Bearer <YOUR_JWT_TOKEN>`

### 5. Update User

- **Method:** `PUT`
- **URL:** `http://localhost:8080/api/auth/users/1`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer <YOUR_JWT_TOKEN>`
- **Body (raw, JSON):**
  ```json
  {
    "username": "updateduser",
    "role": "USER"
  }
  ```

### 6. Delete User

- **Method:** `DELETE`
- **URL:** `http://localhost:8080/api/auth/users/1`
- **Headers:**
  - `Authorization: Bearer <YOUR_JWT_TOKEN>`
