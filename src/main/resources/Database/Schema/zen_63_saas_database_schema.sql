-- Enable UUID extension for secure, non-sequential SaaS identifiers
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. TENANTS TABLE (SaaS Layer)
CREATE TABLE tenants (
     id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
     name VARCHAR(100) NOT NULL,
     subdomain VARCHAR(50) UNIQUE NOT NULL,
     is_active BOOLEAN DEFAULT TRUE,
     created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. ROLES
CREATE TABLE roles (
       id SERIAL PRIMARY KEY,
       name VARCHAR(50) UNIQUE NOT NULL -- 'system_admin', 'owner', 'manager', 'waiter', 'washer', 'customer'
);

-- 3. USERS TABLE (Global Identity Pool)
CREATE TABLE users (
       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
       email VARCHAR(150) UNIQUE NOT NULL,
       password_hash VARCHAR(255) NOT NULL,
       first_name VARCHAR(100) NOT NULL,
       last_name VARCHAR(100) NOT NULL,
       phone VARCHAR(20),
       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. TENANT_USERS (Junction: Links users to businesses)
CREATE TABLE tenant_users (
      tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      role_id INT NOT NULL REFERENCES roles(id),
      is_active BOOLEAN DEFAULT TRUE,
      PRIMARY KEY (tenant_id, user_id)
);

-- 5. RESTAURANT TABLES
CREATE TABLE restaurant_tables (
       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
       tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
       table_number VARCHAR(20) NOT NULL,
       capacity INT NOT NULL DEFAULT 2,
       location_zone VARCHAR(50),
       UNIQUE (tenant_id, table_number)
);

-- 6. CUSTOMER VEHICLES
CREATE TABLE customer_vehicles (
       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
       tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
       customer_id UUID REFERENCES users(id) ON DELETE CASCADE,
       license_plate VARCHAR(20) NOT NULL,
       make VARCHAR(50),
       model VARCHAR(50),
       color VARCHAR(30),
       UNIQUE (tenant_id, license_plate)
);

-- 7. CATEGORIES
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('food', 'beverage', 'car_wash', 'event')),
    name VARCHAR(100) NOT NULL
);

-- 8. CATALOG ITEMS (Unified for Menu and Wash Packages)
CREATE TABLE catalog_items (
   id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
   tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
   category_id UUID NOT NULL REFERENCES categories(id),
   name VARCHAR(150) NOT NULL,
   description TEXT,
   base_price NUMERIC(10, 2) NOT NULL,
   is_available BOOLEAN DEFAULT TRUE,
   deleted_at TIMESTAMP WITH TIME ZONE
);

-- 9. ORDERS (The Master Ticket)
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    table_id UUID REFERENCES restaurant_tables(id) ON DELETE SET NULL,
    customer_id UUID REFERENCES users(id) ON DELETE SET NULL,
    opened_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'kitchen', 'served', 'closed', 'cancelled')),
    total_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 10. ORDER ITEMS (The Line Items)
CREATE TABLE order_items (
     id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
     order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
     catalog_item_id UUID REFERENCES catalog_items(id) ON DELETE SET NULL,
     vehicle_id UUID REFERENCES customer_vehicles(id) ON DELETE SET NULL,
     quantity INT NOT NULL DEFAULT 1,
     unit_price_at_sale NUMERIC(10, 2) NOT NULL, -- 3NF Protection
     notes TEXT,
     operational_status VARCHAR(30) DEFAULT 'pending' CHECK (operational_status IN ('pending', 'in_progress', 'ready', 'delivered'))
);

-- 11. PAYMENTS
CREATE TABLE payments (
      id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
      order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
      tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
      amount NUMERIC(10, 2) NOT NULL,
      tip_amount NUMERIC(10, 2) DEFAULT 0.00,
      payment_method VARCHAR(30) NOT NULL CHECK (payment_method IN ('cash', 'card', 'eft', 'voucher')),
      processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);