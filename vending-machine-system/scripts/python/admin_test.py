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
    
    def test_user_management(self, cleanup: bool = True):
        """Test user management endpoints"""
        self.print_section("USER MANAGEMENT TESTS")
        
        # Define test user credentials
        test_username = f"testuser_{int(time.time())}"
        test_password = "testpass123"
        user_id = None
        user_created = False
        
        # 1. Try to create a new user
        print("\n1. Creating new user...")
        url = f"{self.base_url}/api/auth/users"
        new_user = {
            "username": test_username,
            "password": test_password,
            "role": "ADMIN"  # Changed from USER to ADMIN (valid roles: SUPER_ADMIN, ADMIN)
        }
        response = requests.post(url, json=new_user, headers=self.headers)
        self.print_response(response, "Create User")
        
        if response.status_code in [200, 201]:
            try:
                data = response.json()
                if 'data' in data and 'id' in data['data']:
                    user_id = data['data']['id']
                    user_created = True
                    print(f"  ✓ User created with ID: {user_id}")
            except:
                pass
        elif response.status_code == 400:
            # User might already exist from previous test run
            try:
                error_data = response.json()
                if 'message' in error_data and 'already exists' in error_data['message'].lower():
                    print(f"  ℹ User already exists, will use existing user for testing")
            except:
                pass
        
        # 1b. Test login with created/existing user
        print(f"\n1b. Testing login with test user '{test_username}'...")
        login_url = f"{self.base_url}/api/auth/login"
        login_data = {
            "username": test_username,
            "password": test_password
        }
        login_response = requests.post(login_url, json=login_data)
        self.print_response(login_response, "Login Test User")
        
        if login_response.status_code == 200:
            try:
                login_result = login_response.json()
                if 'data' in login_result and 'token' in login_result['data']:
                    print(f"  ✓ Login successful! Token received.")
                    # If we didn't get user_id from creation, try to get it from login response
                    if not user_id and 'id' in login_result['data']:
                        user_id = login_result['data']['id']
                        print(f"  ✓ Retrieved user ID from login: {user_id}")
            except:
                pass
        else:
            print(f"  ✗ Login failed - test user credentials may be incorrect")
        
        # 2. Get all users
        print("\n2. Fetching all users...")
        url = f"{self.base_url}/api/auth/users"
        response = requests.get(url, headers=self.headers)
        self.print_response(response, "Get All Users")
        
        # If we still don't have user_id, try to find it in the user list
        if not user_id and response.status_code == 200:
            try:
                users_data = response.json()
                if 'data' in users_data:
                    for user in users_data['data']:
                        if user.get('username') == test_username:
                            user_id = user.get('id')
                            print(f"  ✓ Found test user in list with ID: {user_id}")
                            break
            except:
                pass
        
        # 3. Update user if we have user_id
        if user_id:
            print(f"\n3. Updating user {user_id}...")
            url = f"{self.base_url}/api/auth/users/{user_id}"
            updated_username = f"updated_user_{int(time.time())}"
            update_data = {
                "username": updated_username,
                "role": "ADMIN"  # Changed from USER to ADMIN (valid roles: SUPER_ADMIN, ADMIN)
            }
            response = requests.put(url, json=update_data, headers=self.headers)
            self.print_response(response, "Update User")
            
            # Update test_username for later operations
            if response.status_code == 200:
                test_username = updated_username
        
        # 4. Get user by ID
        if user_id:
            print(f"\n4. Fetching user {user_id}...")
            url = f"{self.base_url}/api/auth/users/{user_id}"
            response = requests.get(url, headers=self.headers)
            self.print_response(response, "Get User by ID")
        
        # 5. Delete user (only if cleanup is enabled)
        if cleanup:
            if user_id:
                print(f"\n5. Deleting user {user_id}...")
                url = f"{self.base_url}/api/auth/users/{user_id}"
                response = requests.delete(url, headers=self.headers)
                self.print_response(response, "Delete User")
        else:
            if user_id:
                print(f"\n5. Skipping user deletion (cleanup disabled)")
                print(f"  ℹ User '{test_username}' (ID: {user_id}) has been kept for inspection")
    
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
                "quantity": 10 + i,
                "minThreshold": 5  # Added required minThreshold field
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
                "quantity": 50,
                "minThreshold": 10  # Added minThreshold field
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
        
        # Run all test sections (cleanup applies to both users and products)
        time.sleep(1)
        self.test_user_management(cleanup=cleanup_products)
        
        time.sleep(1)
        self.test_product_management(cleanup=cleanup_products)
        
        time.sleep(1)
        self.test_payment_transactions()
        
        # Summary
        self.print_section("TEST SUITE COMPLETED")
        print("\nAll admin endpoint tests have been executed.")
        print("Review the results above for any failures or issues.\n")
    
    def run_selected_tests(self, test_selection: list, cleanup_products: bool = True):
        """Run only selected tests"""
        print("\n" + "█"*60)
        print("  VENDING MACHINE ADMIN TEST SUITE (SELECTED TESTS)")
        print("█"*60)
        
        # Login
        if not self.login(ADMIN_USERNAME, ADMIN_PASSWORD):
            print("\n✗ Cannot proceed without successful login!")
            print("\nMake sure the system is running and admin user exists.")
            return
        
        # Run selected test sections (cleanup applies to both users and products)
        if '1' in test_selection:
            time.sleep(1)
            self.test_user_management(cleanup=cleanup_products)
        
        if '2' in test_selection:
            time.sleep(1)
            self.test_product_management(cleanup=cleanup_products)
        
        if '3' in test_selection:
            time.sleep(1)
            self.test_payment_transactions()
        
        # Summary
        self.print_section("TEST SUITE COMPLETED")
        print("\nSelected admin endpoint tests have been executed.")
        print("Review the results above for any failures or issues.\n")

def show_menu():
    """Display interactive menu"""
    print("\n" + "="*60)
    print("  VENDING MACHINE ADMIN TEST MENU")
    print("="*60)
    print("\nAvailable Test Suites:")
    print("  1. User Management Tests")
    print("     - Create user")
    print("     - Get all users")
    print("     - Update user")
    print("     - Get user by ID")
    print("     - Delete user")
    print()
    print("  2. Product Management Tests")
    print("     - Get all products")
    print("     - Create 11 new products")
    print("     - Update product")
    print("     - Delete product")
    print("     - Verify operations")
    print()
    print("  3. Payment Transaction Tests")
    print("     - Get all payment transactions")
    print()
    print("  0. Run ALL Tests")
    print("  q. Quit")
    print("="*60)
    
def get_user_choice():
    """Get user's test selection"""
    while True:
        choice = input("\nEnter test numbers (comma-separated, e.g., 1,2) or 0 for all, q to quit: ").strip()
        
        if choice.lower() == 'q':
            return None
        
        if choice == '0':
            return ['1', '2', '3']
        
        # Parse comma-separated values
        selections = [s.strip() for s in choice.split(',')]
        valid_selections = [s for s in selections if s in ['1', '2', '3']]
        
        if valid_selections:
            return valid_selections
        
        print("Invalid selection. Please enter valid test numbers (1, 2, 3), 0 for all, or q to quit.")
    
def ask_cleanup_preference():
    """Ask user if they want to cleanup test products"""
    while True:
        choice = input("\nCleanup test products after execution? (y/n, default: y): ").strip().lower()
        if choice == '' or choice == 'y':
            return True
        elif choice == 'n':
            return False
        else:
            print("Invalid choice. Please enter 'y' for yes or 'n' for no.")

def main():
    """Main entry point"""
    print("\nVending Machine Admin Test Script")
    print(f"Target URL: {BASE_URL}")
    print(f"Admin User: {ADMIN_USERNAME}\n")

    # Check for command-line arguments for non-interactive mode
    if len(sys.argv) > 1:
        # Legacy support for --no-cleanup flag
        cleanup_products = "--no-cleanup" not in sys.argv
        if not cleanup_products:
            print("*** Running with --no-cleanup: Products will not be deleted after the test. ***\n")
        
        # Check for --all flag to run all tests non-interactively
        if "--all" in sys.argv:
            print("*** Running in non-interactive mode: All tests will be executed ***\n")
        else:
            print("*** Running in non-interactive mode: All tests will be executed ***\n")
            print("*** Use interactive mode (no args) for test selection menu ***\n")
    else:
        # Interactive mode
        show_menu()
        test_selection = get_user_choice()
        
        if test_selection is None:
            print("\n👋 Exiting. No tests executed.")
            return
        
        cleanup_products = ask_cleanup_preference()
        print(f"\nCleanup preference: {'Yes' if cleanup_products else 'No'}")
    
    # Check if services are accessible
    try:
        response = requests.get(f"{BASE_URL}/api/inventory/products", timeout=5)
        print(f"\n✓ Services are accessible (Status: {response.status_code})\n")
    except Exception as e:
        print(f"\n✗ Cannot connect to services: {str(e)}")
        print("\nPlease ensure:")
        print("1. All microservices are running")
        print("2. API Gateway is accessible at http://localhost:8080")
        print("3. Admin user has been created\n")
        return
    
    # Run tests
    tester = VendingMachineAdminTester(BASE_URL)
    
    if len(sys.argv) > 1 or 'test_selection' not in locals():
        # Non-interactive mode or no selection made
        tester.run_all_tests(cleanup_products=cleanup_products)
    else:
        # Interactive mode with selection
        tester.run_selected_tests(test_selection, cleanup_products=cleanup_products)

if __name__ == "__main__":
    main()