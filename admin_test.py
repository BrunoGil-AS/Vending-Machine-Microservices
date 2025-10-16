#!/usr/bin/env python3
"""
Admin Test Script for Vending Machine Microservices
This script simulates an administrator testing all admin endpoints:
- User authentication and management
- Product creation, updating, and deletion
- Stock management
- Transaction history viewing
"""

import requests
import json
import time
from typing import Dict, Optional

# Configuration
BASE_URL = "http://localhost:8080"
ADMIN_USERNAME = "admin"
ADMIN_PASSWORD = "admin123"

class VendingMachineAdminTester:
    def __init__(self, base_url: str):
        self.base_url = base_url
        self.token: Optional[str] = None
        self.headers: Dict[str, str] = {"Content-Type": "application/json"}
        
    def print_section(self, title: str):
        """Print a formatted section header"""
        print("\n" + "="*60)
        print(f"  {title}")
        print("="*60)
    
    def print_response(self, response: requests.Response, action: str):
        """Print formatted response"""
        print(f"\n{action}:")
        print(f"Status Code: {response.status_code}")
        try:
            print(f"Response: {json.dumps(response.json(), indent=2)}")
        except:
            print(f"Response: {response.text}")
        return response
    
    def login(self, username: str, password: str) -> bool:
        """Login as admin and get JWT token"""
        self.print_section("ADMIN LOGIN")
        
        url = f"{self.base_url}/api/auth/login"
        payload = {
            "username": username,
            "password": password
        }
        
        try:
            response = requests.post(url, json=payload, headers=self.headers)
            self.print_response(response, "Login Attempt")
            
            if response.status_code == 200:
                data = response.json()
                if data.get('success') and 'data' in data and 'token' in data['data']:
                    self.token = data['data']['token']
                    self.headers["Authorization"] = f"Bearer {self.token}"
                    print("\n✓ Login successful! Token acquired.")
                    return True
            
            print("\n✗ Login failed!")
            return False
        except Exception as e:
            print(f"\n✗ Login error: {str(e)}")
            return False
    
    def test_user_management(self):
        """Test user management endpoints"""
        self.print_section("USER MANAGEMENT TESTS")
        
        # Create a new user
        print("\n1. Creating new user...")
        url = f"{self.base_url}/api/auth/users"
        new_user = {
            "username": f"testuser_{int(time.time())}",
            "password": "testpass123",
            "role": "USER"
        }
        response = requests.post(url, json=new_user, headers=self.headers)
        self.print_response(response, "Create User")
        
        user_id = None
        if response.status_code in [200, 201]:
            try:
                data = response.json()
                if 'data' in data and 'id' in data['data']:
                    user_id = data['data']['id']
                    print(f"\n✓ User created with ID: {user_id}")
            except:
                pass
        
        # Get all users
        print("\n2. Fetching all users...")
        url = f"{self.base_url}/api/auth/users"
        response = requests.get(url, headers=self.headers)
        self.print_response(response, "Get All Users")
        
        # Update user if we have user_id
        if user_id:
            print(f"\n3. Updating user {user_id}...")
            url = f"{self.base_url}/api/auth/users/{user_id}"
            update_data = {
                "username": f"updated_user_{int(time.time())}",
                "role": "USER"
            }
            response = requests.put(url, json=update_data, headers=self.headers)
            self.print_response(response, "Update User")
        
        # Get user by ID
        if user_id:
            print(f"\n4. Fetching user {user_id}...")
            url = f"{self.base_url}/api/auth/users/{user_id}"
            response = requests.get(url, headers=self.headers)
            self.print_response(response, "Get User by ID")
        
        # Delete user
        if user_id:
            print(f"\n5. Deleting user {user_id}...")
            url = f"{self.base_url}/api/auth/users/{user_id}"
            response = requests.delete(url, headers=self.headers)
            self.print_response(response, "Delete User")
    
    def test_product_management(self):
        """Test product management endpoints"""
        self.print_section("PRODUCT MANAGEMENT TESTS")
        
        # Get all products (check initial state)
        print("\n1. Fetching all products (initial state)...")
        url = f"{self.base_url}/api/inventory/products"
        response = requests.get(url)  # Public endpoint, no auth needed
        self.print_response(response, "Get All Products")
        
        # Create a new product
        print("\n2. Creating new product...")
        url = f"{self.base_url}/api/admin/inventory/products"
        new_product = {
            "name": f"Test Product {int(time.time())}",
            "price": 2.50,
            "description": "A test product for admin testing",
            "quantity": 10
        }
        response = requests.post(url, json=new_product, headers=self.headers)
        self.print_response(response, "Create Product")
        
        product_id = None
        if response.status_code in [200, 201]:
            try:
                data = response.json()
                if 'id' in data:
                    product_id = data['id']
                    print(f"\n✓ Product created with ID: {product_id}")
            except:
                pass
        
        # Create another product
        print("\n3. Creating another product...")
        url = f"{self.base_url}/api/admin/inventory/products"
        another_product = {
            "name": f"Premium Snack {int(time.time())}",
            "price": 3.75,
            "description": "Premium test product",
            "quantity": 15
        }
        response = requests.post(url, json=another_product, headers=self.headers)
        self.print_response(response, "Create Another Product")
        
        product_id_2 = None
        if response.status_code in [200, 201]:
            try:
                data = response.json()
                if 'id' in data:
                    product_id_2 = data['id']
                    print(f"\n✓ Second product created with ID: {product_id_2}")
            except:
                pass
        
        # Update stock for first product
        if product_id:
            print(f"\n4. Updating stock for product {product_id}...")
            url = f"{self.base_url}/api/admin/inventory/stock/{product_id}"
            stock_update = {
                "quantity": 25,
                "minThreshold": 5
            }
            response = requests.put(url, json=stock_update, headers=self.headers)
            self.print_response(response, "Update Stock")
        
        # Check product availability
        if product_id:
            print(f"\n5. Checking availability for product {product_id}...")
            url = f"{self.base_url}/api/inventory/availability/{product_id}"
            response = requests.get(url)  # Public endpoint
            self.print_response(response, "Get Product Availability")
        
        # Get all products again (after additions)
        print("\n6. Fetching all products (after additions)...")
        url = f"{self.base_url}/api/inventory/products"
        response = requests.get(url)
        self.print_response(response, "Get All Products (Updated)")
        
        # Delete first product
        if product_id:
            print(f"\n7. Deleting product {product_id}...")
            url = f"{self.base_url}/api/admin/inventory/products/{product_id}"
            response = requests.delete(url, headers=self.headers)
            self.print_response(response, "Delete Product")
        
        # Verify deletion
        print("\n8. Fetching all products (after deletion)...")
        url = f"{self.base_url}/api/inventory/products"
        response = requests.get(url)
        self.print_response(response, "Get All Products (After Deletion)")
        
        # Clean up second product
        if product_id_2:
            print(f"\n9. Cleaning up: Deleting product {product_id_2}...")
            url = f"{self.base_url}/api/admin/inventory/products/{product_id_2}"
            response = requests.delete(url, headers=self.headers)
            self.print_response(response, "Delete Second Product")
    
    def test_payment_transactions(self):
        """Test viewing payment transactions"""
        self.print_section("PAYMENT TRANSACTION TESTS")
        
        print("\n1. Fetching all payment transactions...")
        url = f"{self.base_url}/api/admin/payment/transactions"
        response = requests.get(url, headers=self.headers)
        self.print_response(response, "Get Payment Transactions")
    
    def run_all_tests(self):
        """Run all admin tests"""
        print("\n" + "█"*60)
        print("  VENDING MACHINE ADMIN TEST SUITE")
        print("█"*60)
        
        # Login
        if not self.login(ADMIN_USERNAME, ADMIN_PASSWORD):
            print("\n✗ Cannot proceed without successful login!")
            print("\nMake sure the system is running and admin user exists.")
            print("To create admin user, run:")
            print(f'curl -X POST {self.base_url}/api/auth/users -H "Content-Type: application/json" -d \'{{"username": "{ADMIN_USERNAME}", "password": "{ADMIN_PASSWORD}", "role": "SUPER_ADMIN"}}\'')
            return
        
        # Run all test sections
        time.sleep(1)
        self.test_user_management()
        
        time.sleep(1)
        self.test_product_management()
        
        time.sleep(1)
        self.test_payment_transactions()
        
        # Summary
        self.print_section("TEST SUITE COMPLETED")
        print("\nAll admin endpoint tests have been executed.")
        print("Review the results above for any failures or issues.\n")

def main():
    """Main entry point"""
    print("\nVending Machine Admin Test Script")
    print(f"Target URL: {BASE_URL}")
    print(f"Admin User: {ADMIN_USERNAME}\n")
    
    # Check if services are accessible
    try:
        response = requests.get(f"{BASE_URL}/api/inventory/products", timeout=5)
        print(f"✓ Services are accessible (Status: {response.status_code})\n")
    except Exception as e:
        print(f"✗ Cannot connect to services: {str(e)}")
        print("\nPlease ensure:")
        print("1. All microservices are running")
        print("2. API Gateway is accessible at http://localhost:8080")
        print("3. Admin user has been created\n")
        return
    
    # Run tests
    tester = VendingMachineAdminTester(BASE_URL)
    tester.run_all_tests()

if __name__ == "__main__":
    main()
