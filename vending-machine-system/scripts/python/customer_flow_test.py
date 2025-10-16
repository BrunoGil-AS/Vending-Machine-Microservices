#!/usr/bin/env python3
"""
Customer Flow Test Script for Vending Machine Microservices
This script simulates normal customer usage scenarios:
- Browsing available products
- Purchasing single products with different payment methods
- Purchasing multiple products with different payment methods
"""

import requests
import json
import time
from typing import Dict, List, Optional

# Configuration
BASE_URL = "http://localhost:8080"

class VendingMachineCustomerTester:
    def __init__(self, base_url: str):
        self.base_url = base_url
        self.headers: Dict[str, str] = {"Content-Type": "application/json"}
        self.available_products: List[Dict] = []
        
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
    
    def browse_products(self) -> List[Dict]:
        """Browse available products"""
        self.print_section("BROWSING PRODUCTS")
        
        print("\nFetching available products...")
        url = f"{self.base_url}/api/inventory/products"
        
        try:
            response = requests.get(url, headers=self.headers)
            self.print_response(response, "Get Products")
            
            if response.status_code == 200:
                products = response.json()
                self.available_products = products
                
                if products:
                    print(f"\n✓ Found {len(products)} products available:")
                    for i, product in enumerate(products, 1):
                        print(f"\n  {i}. {product.get('name', 'Unknown')}")
                        print(f"     Price: ${product.get('price', 0):.2f}")
                        print(f"     Description: {product.get('description', 'N/A')}")
                        print(f"     Product ID: {product.get('id', 'N/A')}")
                else:
                    print("\n⚠ No products available in the vending machine!")
                
                return products
            else:
                print("\n✗ Failed to fetch products!")
                return []
        except Exception as e:
            print(f"\n✗ Error browsing products: {str(e)}")
            return []
    
    def check_availability(self, product_id: int):
        """Check product availability"""
        print(f"\nChecking availability for product {product_id}...")
        url = f"{self.base_url}/api/inventory/availability/{product_id}"
        
        try:
            response = requests.get(url, headers=self.headers)
            self.print_response(response, "Check Availability")
            
            if response.status_code == 200:
                stock = response.json()
                quantity = stock.get('quantity', 0)
                print(f"\n✓ Product has {quantity} units available")
                return quantity > 0
            else:
                print("\n✗ Product not available or doesn't exist")
                return False
        except Exception as e:
            print(f"\n✗ Error checking availability: {str(e)}")
            return False
    
    def purchase_single_product(self, product_id: int, payment_method: str):
        """Purchase a single product"""
        self.print_section(f"SINGLE PRODUCT PURCHASE - Payment: {payment_method}")
        
        # Check availability first
        if not self.check_availability(product_id):
            print("\n⚠ Skipping purchase - product not available")
            return None
        
        print(f"\nAttempting to purchase product {product_id} with {payment_method}...")
        url = f"{self.base_url}/api/transaction/purchase"
        
        purchase_request = {
            "items": [
                {
                    "productId": product_id,
                    "quantity": 1
                }
            ]
        }
        
        try:
            response = requests.post(url, json=purchase_request, headers=self.headers)
            self.print_response(response, "Purchase Request")
            
            if response.status_code == 200:
                transaction = response.json()
                print(f"\n✓ Purchase successful!")
                print(f"   Transaction ID: {transaction.get('id', 'N/A')}")
                print(f"   Total Amount: ${transaction.get('totalAmount', 0):.2f}")
                print(f"   Status: {transaction.get('status', 'N/A')}")
                return transaction
            else:
                print("\n✗ Purchase failed!")
                return None
        except Exception as e:
            print(f"\n✗ Error during purchase: {str(e)}")
            return None
    
    def purchase_multiple_products(self, items: List[Dict[str, int]], payment_method: str):
        """Purchase multiple products in one transaction"""
        self.print_section(f"MULTIPLE PRODUCTS PURCHASE - Payment: {payment_method}")
        
        # Check availability for all items
        print("\nChecking availability for all items...")
        all_available = True
        for item in items:
            product_id = item['productId']
            if not self.check_availability(product_id):
                print(f"⚠ Product {product_id} not available")
                all_available = False
        
        if not all_available:
            print("\n⚠ Skipping purchase - some products not available")
            return None
        
        print(f"\nAttempting to purchase {len(items)} different products with {payment_method}...")
        url = f"{self.base_url}/api/transaction/purchase"
        
        purchase_request = {
            "items": [
                {
                    "productId": item['productId'],
                    "quantity": item['quantity']
                }
                for item in items
            ]
        }
        
        print(f"Purchase details:")
        for item in items:
            print(f"  - Product {item['productId']}: {item['quantity']} unit(s)")
        
        try:
            response = requests.post(url, json=purchase_request, headers=self.headers)
            self.print_response(response, "Purchase Request")
            
            if response.status_code == 200:
                transaction = response.json()
                print(f"\n✓ Purchase successful!")
                print(f"   Transaction ID: {transaction.get('id', 'N/A')}")
                print(f"   Total Amount: ${transaction.get('totalAmount', 0):.2f}")
                print(f"   Status: {transaction.get('status', 'N/A')}")
                print(f"   Items purchased: {len(transaction.get('items', []))}")
                return transaction
            else:
                print("\n✗ Purchase failed!")
                return None
        except Exception as e:
            print(f"\n✗ Error during purchase: {str(e)}")
            return None
    
    def run_customer_scenarios(self):
        """Run all customer flow scenarios"""
        print("\n" + "█"*60)
        print("  VENDING MACHINE CUSTOMER FLOW TEST SUITE")
        print("█"*60)
        
        # Scenario 1: Browse products
        products = self.browse_products()
        
        if not products:
            print("\n⚠ No products available for testing!")
            print("\nPlease ensure:")
            print("1. The inventory service is running")
            print("2. Products have been added to the system")
            print("3. Use admin_test.py to add products first")
            return
        
        time.sleep(2)
        
        # Get product IDs for testing
        product_ids = [p['id'] for p in products[:3]]  # Use first 3 products
        
        if len(product_ids) >= 1:
            # Scenario 2: Single product purchase with CASH
            self.print_section("SCENARIO 1: Single Product - Cash Payment")
            time.sleep(1)
            self.purchase_single_product(product_ids[0], "CASH")
            time.sleep(2)
            
            # Scenario 3: Single product purchase with CREDIT_CARD
            if len(product_ids) >= 2:
                self.print_section("SCENARIO 2: Single Product - Credit Card Payment")
                time.sleep(1)
                self.purchase_single_product(product_ids[1], "CREDIT_CARD")
                time.sleep(2)
            
            # Scenario 4: Single product purchase with DEBIT_CARD
            if len(product_ids) >= 1:
                self.print_section("SCENARIO 3: Single Product - Debit Card Payment")
                time.sleep(1)
                self.purchase_single_product(product_ids[0], "DEBIT_CARD")
                time.sleep(2)
        
        # Scenario 5: Multiple products with CASH
        if len(product_ids) >= 2:
            self.print_section("SCENARIO 4: Multiple Products - Cash Payment")
            time.sleep(1)
            items = [
                {"productId": product_ids[0], "quantity": 1},
                {"productId": product_ids[1], "quantity": 2}
            ]
            self.purchase_multiple_products(items, "CASH")
            time.sleep(2)
        
        # Scenario 6: Multiple products with CREDIT_CARD
        if len(product_ids) >= 3:
            self.print_section("SCENARIO 5: Multiple Products - Credit Card Payment")
            time.sleep(1)
            items = [
                {"productId": product_ids[0], "quantity": 1},
                {"productId": product_ids[1], "quantity": 1},
                {"productId": product_ids[2], "quantity": 1}
            ]
            self.purchase_multiple_products(items, "CREDIT_CARD")
            time.sleep(2)
        elif len(product_ids) >= 2:
            self.print_section("SCENARIO 5: Multiple Products - Credit Card Payment")
            time.sleep(1)
            items = [
                {"productId": product_ids[0], "quantity": 2},
                {"productId": product_ids[1], "quantity": 1}
            ]
            self.purchase_multiple_products(items, "CREDIT_CARD")
            time.sleep(2)
        
        # Scenario 7: Multiple products with DEBIT_CARD
        if len(product_ids) >= 2:
            self.print_section("SCENARIO 6: Multiple Products - Debit Card Payment")
            time.sleep(1)
            items = [
                {"productId": product_ids[0], "quantity": 1},
                {"productId": product_ids[1], "quantity": 3}
            ]
            self.purchase_multiple_products(items, "DEBIT_CARD")
        
        # Summary
        self.print_section("TEST SUITE COMPLETED")
        print("\nAll customer flow scenarios have been executed.")
        print("Review the results above for any failures or issues.")
        print("\nScenarios tested:")
        print("  ✓ Product browsing")
        print("  ✓ Availability checking")
        print("  ✓ Single product purchases (CASH, CREDIT_CARD, DEBIT_CARD)")
        print("  ✓ Multiple product purchases (CASH, CREDIT_CARD, DEBIT_CARD)")
        print()

def main():
    """Main entry point"""
    print("\nVending Machine Customer Flow Test Script")
    print(f"Target URL: {BASE_URL}\n")
    
    # Check if services are accessible
    try:
        response = requests.get(f"{BASE_URL}/api/inventory/products", timeout=5)
        print(f"✓ Services are accessible (Status: {response.status_code})\n")
    except Exception as e:
        print(f"✗ Cannot connect to services: {str(e)}")
        print("\nPlease ensure:")
        print("1. All microservices are running")
        print("2. API Gateway is accessible at http://localhost:8080")
        print("3. Products exist in the inventory\n")
        return
    
    # Run scenarios
    tester = VendingMachineCustomerTester(BASE_URL)
    tester.run_customer_scenarios()

if __name__ == "__main__":
    main()