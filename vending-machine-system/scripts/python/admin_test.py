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
import sys
from typing import Dict, Optional

# Configuration
BASE_URL = "http://localhost:8080"
ADMIN_USERNAME = "hardcoded-admin"
ADMIN_PASSWORD = "password123"

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
    
    def test_product_management(self, cleanup: bool = True):
        """Test product management endpoints"""
        self.print_section("PRODUCT MANAGEMENT TESTS")

        # 1. Get all products (initial state)
        print("\n1. Fetching all products (initial state)...")
        url = f"{self.base_url}/api/inventory/products"
        response = requests.get(url)
        self.print_response(response, "Get All Products (Initial)")

        # 2. Add 11 new products
        print("\n2. Creating 11 new products...")
        product_ids = []
        for i in range(11):
            url = f"{self.base_url}/api/admin/inventory/products"
            new_product = {
                "name": f"Product {i+1} - {int(time.time())}",
                "price": round(1.0 + i * 0.5, 2),
                "description": f"Description for product {i+1}",
                "quantity": 10 + i
            }
            response = requests.post(url, json=new_product, headers=self.headers)
            self.print_response(response, f"Create Product {i+1}")
            if response.status_code in [200, 201]:
                try:
                    data = response.json()
                    if 'id' in data:
                        product_id = data['id']
                        product_ids.append(product_id)
                        print(f"  ✓ Product {i+1} created with ID: {product_id}")
                except Exception as e:
                    print(f"  ✗ Could not parse response for product {i+1}: {e}")
        
        print(f"\nCreated {len(product_ids)} products.")
        
        # 3. Get all products (after additions)
        print("\n3. Fetching all products (after additions)...")
        url = f"{self.base_url}/api/inventory/products"
        response = requests.get(url)
        self.print_response(response, "Get All Products (After Additions)")

        # 4. Modify one product
        if product_ids:
            product_to_modify_id = product_ids[0]
            print(f"\n4. Modifying product {product_to_modify_id}...")
            url = f"{self.base_url}/api/admin/inventory/products/{product_to_modify_id}"
            updated_product = {
                "name": f"Modified Product {int(time.time())}",
                "price": 9.99,
                "description": "This product has been updated.",
                "quantity": 50
            }
            response = requests.put(url, json=updated_product, headers=self.headers)
            self.print_response(response, f"Update Product {product_to_modify_id}")

        # 5. Get product by ID to verify modification
        if product_ids:
            product_to_verify_id = product_ids[0]
            print(f"\n5. Verifying modification for product {product_to_verify_id}...")
            url = f"{self.base_url}/api/inventory/products/{product_to_verify_id}"
            response = requests.get(url)
            self.print_response(response, f"Get Product by ID {product_to_verify_id}")

        # 6. Delete one product
        if len(product_ids) > 1:
            product_to_delete_id = product_ids[1]
            print(f"\n6. Deleting product {product_to_delete_id}...")
            url = f"{self.base_url}/api/admin/inventory/products/{product_to_delete_id}"
            response = requests.delete(url, headers=self.headers)
            self.print_response(response, f"Delete Product {product_to_delete_id}")
            if response.status_code in [200, 204]:
                product_ids.pop(1)

        # 7. Verify deletion by trying to fetch the deleted product
        if 'product_to_delete_id' in locals():
            print(f"\n7. Verifying deletion of product {product_to_delete_id}...")
            url = f"{self.base_url}/api/inventory/products/{product_to_delete_id}"
            response = requests.get(url)
            self.print_response(response, f"Get Deleted Product by ID {product_to_delete_id}")

        # 8. Get all products (after modifications and deletion)
        print("\n8. Fetching all products (after changes)...")
        url = f"{self.base_url}/api/inventory/products"
        response = requests.get(url)
        self.print_response(response, "Get All Products (After Changes)")

        # 9. Clean up remaining products
        if cleanup:
            print("\n9. Cleaning up remaining products...")
            if product_ids:
                for product_id in product_ids:
                    print(f"  - Deleting product {product_id}...")
                    url = f"{self.base_url}/api/admin/inventory/products/{product_id}"
                    response = requests.delete(url, headers=self.headers)
                    self.print_response(response, f"Cleanup: Delete Product {product_id}")
            else:
                print("  - No products to clean up.")
        else:
            print("\n9. Skipping product cleanup.")
    
    def test_payment_transactions(self):
        """Test viewing payment transactions"""
        self.print_section("PAYMENT TRANSACTION TESTS")
        
        print("\n1. Fetching all payment transactions...")
        url = f"{self.base_url}/api/admin/payment/transactions"
        response = requests.get(url, headers=self.headers)
        self.print_response(response, "Get Payment Transactions")
    
    def run_all_tests(self, cleanup_products: bool = True):
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
        self.test_product_management(cleanup=cleanup_products)
        
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

    # Check for --no-cleanup argument
    cleanup_products = "--no-cleanup" not in sys.argv
    if not cleanup_products:
        print("*** Running with --no-cleanup: Products will not be deleted after the test. ***\n")

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
    tester.run_all_tests(cleanup_products=cleanup_products)

if __name__ == "__main__":
    main()