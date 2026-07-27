-- V7__reset_and_seed_reference_data.sql
--
-- Clears the throwaway data left behind by the M4-M7 verification runs and replaces it with a
-- realistic menu, ingredient inventory and recipe set for "La Braise".
--
-- WHY THIS IS A MIGRATION AND NOT A SCRIPT: the reference data below is now part of what the
-- application *is* - every screen is demonstrated against it - so it belongs under version control
-- and has to arrive the same way the schema does. Flyway also guarantees it runs exactly once,
-- which matters because the first half of this file is destructive.
--
-- WHAT IT DELETES: every order, order line, payment and inventory adjustment, plus all menu
-- categories, menu items, ingredients and recipes that existed beforehand. Those were test
-- fixtures ("M4 Test", "M6 Pizza 023309549", "C Shared 023627404" and ~86 synthetic orders), not
-- real trading history. Staff accounts (app_user) and the floor plan (restaurant_table) are NOT
-- touched.
--
-- Delete order follows the foreign keys inward: payment -> order_item -> orders, then
-- inventory_adjustment and recipe_item before the rows they reference. Every FK here is RESTRICT
-- (except recipe_item, which cascades from menu_item), so the sequence is load-bearing.

-- ---------------------------------------------------------------------------
-- 1. Clear transactional history.
-- ---------------------------------------------------------------------------
DELETE FROM payment;
DELETE FROM order_item;
DELETE FROM orders;

-- The FR-21 audit log is immutable *through the application*; this one-time reset removes test
-- adjustments that refer to ingredients about to be deleted. No production data exists yet.
DELETE FROM inventory_adjustment;

-- ---------------------------------------------------------------------------
-- 2. Clear reference data.
-- ---------------------------------------------------------------------------
DELETE FROM recipe_item;
DELETE FROM menu_item;
DELETE FROM menu_category;
DELETE FROM ingredient;

-- Every table is free again now that no order is open.
UPDATE restaurant_table SET status = 'AVAILABLE';

-- Ids restart at 1. Safe only because the tables above are now empty.
SELECT setval(pg_get_serial_sequence('menu_category', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('menu_item', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('ingredient', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('recipe_item', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('orders', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('order_item', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('payment', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('inventory_adjustment', 'id'), 1, false);

-- ---------------------------------------------------------------------------
-- 3. Menu categories (FR-03).
-- ---------------------------------------------------------------------------
INSERT INTO menu_category (name, sort_order) VALUES
    ('Starters',  10),
    ('Mains',     20),
    ('Sides',     30),
    ('Desserts',  40),
    ('Beverages', 50);

-- ---------------------------------------------------------------------------
-- 4. Ingredients (FR-17, FR-18).
--
-- Stock levels and thresholds are set as a working kitchen would hold them. Two are deliberately
-- at or below threshold so the manager dashboard's low-stock panel (FR-20) and the FR-18 alert
-- have something real to show on a fresh install rather than an empty state that proves nothing.
-- ---------------------------------------------------------------------------
INSERT INTO ingredient (name, unit, stock_qty, low_stock_threshold) VALUES
    ('Beef Sirloin',    'g',   15000,  3000),
    ('Chicken Breast',  'g',   12000,  2500),
    ('Salmon Fillet',   'g',    1500,  2000),   -- low: running out
    ('Bacon',           'g',    4000,   800),
    ('Potato',          'g',   25000,  5000),
    ('Tomato',          'g',    8000,  1500),
    ('Onion',           'g',   10000,  2000),
    ('Garlic',          'g',    2000,   400),
    ('Mushroom',        'g',    5000,  1000),
    ('Mixed Leaves',    'g',    3000,   600),
    ('Basil',           'g',     500,   100),
    ('Lemon',           'pcs',     60,    12),
    ('Orange',          'pcs',     80,    20),
    ('Butter',          'g',    6000,  1000),
    ('Double Cream',    'ml',   4000,   800),
    ('Milk',            'ml',  20000,  4000),
    ('Parmesan',        'g',    3000,   600),
    ('Mozzarella',      'g',    4000,   800),
    ('Eggs',            'pcs',    240,    48),
    ('Plain Flour',     'g',   15000,  3000),
    ('Caster Sugar',    'g',    8000,  1500),
    ('Dark Chocolate',  'g',    3000,   600),
    ('Vanilla Extract', 'ml',    500,   100),
    ('Olive Oil',       'ml',   5000,  1000),
    ('Truffle Oil',     'ml',    120,   250),   -- low: expensive, ordered rarely
    ('Baguette',        'pcs',     40,    10),
    ('Coffee Beans',    'g',    5000,  1000),
    ('Red Wine',        'ml',   9000,  1500),
    ('Bottled Water',   'pcs',    120,    24),
    ('Soft Drink Can',  'pcs',    150,    30);

-- ---------------------------------------------------------------------------
-- 5. Menu items (FR-04).
--
-- "Mushroom Croquettes" ships unavailable on purpose: FR-05 requires a waiter to be stopped with a
-- clear message when they try to order something that is off, and that path is only demonstrable
-- if something is actually off.
-- ---------------------------------------------------------------------------
INSERT INTO menu_item (name, description, price, category_id, available)
SELECT v.name, v.description, v.price, c.id, v.available
FROM (VALUES
    ('Garlic Bread',         'Toasted baguette, garlic butter, parsley',                  4.50, 'Starters',  TRUE),
    ('Bruschetta',           'Vine tomato, basil, olive oil on grilled baguette',         6.50, 'Starters',  TRUE),
    ('Soup of the Day',      'Ask your server - served with bread',                       6.00, 'Starters',  TRUE),
    ('Mushroom Croquettes',  'Creamy wild mushroom, parmesan crust',                      8.50, 'Starters',  FALSE),

    ('Steak Frites',         '250g sirloin, thick-cut chips, herb butter',               22.00, 'Mains',     TRUE),
    ('Grilled Salmon',       'Salmon fillet, lemon, dressed leaves',                     19.50, 'Mains',     TRUE),
    ('Chicken Milanese',     'Breaded chicken breast, parmesan, rocket',                 17.00, 'Mains',     TRUE),
    ('Mushroom Risotto',     'Slow-stirred arborio, wild mushroom, parmesan',            15.50, 'Mains',     TRUE),
    ('Margherita Pizza',     'Stone-baked, San Marzano tomato, mozzarella, basil',       13.00, 'Mains',     TRUE),
    ('Bacon Cheeseburger',   'Sirloin patty, smoked bacon, melted mozzarella',           16.00, 'Mains',     TRUE),

    ('Truffle Fries',        'Thick-cut chips, truffle oil, parmesan',                    6.50, 'Sides',     TRUE),
    ('Garden Salad',         'Mixed leaves, tomato, house dressing',                      5.00, 'Sides',     TRUE),
    ('Sauteed Mushrooms',    'Garlic butter, flat-leaf parsley',                          5.50, 'Sides',     TRUE),

    ('Chocolate Fondant',    'Dark chocolate, molten centre, cream',                      8.00, 'Desserts',  TRUE),
    ('Creme Brulee',         'Vanilla custard, burnt sugar crust',                        7.50, 'Desserts',  TRUE),
    ('Lemon Tart',           'Sharp lemon curd, butter pastry',                           7.00, 'Desserts',  TRUE),

    ('Espresso',             'Double shot',                                               3.00, 'Beverages', TRUE),
    ('Cappuccino',           'Double shot, steamed milk',                                 4.00, 'Beverages', TRUE),
    ('Fresh Orange Juice',   'Squeezed to order',                                         4.50, 'Beverages', TRUE),
    ('House Red',            '175ml glass',                                               7.00, 'Beverages', TRUE),
    ('Bottled Water',        '500ml still',                                               2.50, 'Beverages', TRUE),
    ('Soft Drink',           'Chilled can',                                               3.00, 'Beverages', TRUE)
) AS v(name, description, price, category, available)
JOIN menu_category c ON c.name = v.category;

-- ---------------------------------------------------------------------------
-- 6. Recipes (the basis for FR-08's stock check and FR-19's deduction at payment).
--
-- Quantities are per single portion, in each ingredient's own unit. Beverages that are simply
-- served from stock (water, cans) map one-to-one; dishes draw from several ingredients, which is
-- what makes the FR-19 deduction and the NFR-07 locking worth having.
-- ---------------------------------------------------------------------------
INSERT INTO recipe_item (menu_item_id, ingredient_id, quantity)
SELECT m.id, i.id, v.quantity
FROM (VALUES
    ('Garlic Bread',        'Baguette',        0.500),
    ('Garlic Bread',        'Butter',         30.000),
    ('Garlic Bread',        'Garlic',          8.000),

    ('Bruschetta',          'Baguette',        0.400),
    ('Bruschetta',          'Tomato',         90.000),
    ('Bruschetta',          'Basil',           5.000),
    ('Bruschetta',          'Olive Oil',      10.000),

    ('Soup of the Day',     'Onion',          80.000),
    ('Soup of the Day',     'Potato',        120.000),
    ('Soup of the Day',     'Double Cream',   40.000),
    ('Soup of the Day',     'Butter',         15.000),

    ('Mushroom Croquettes', 'Mushroom',      120.000),
    ('Mushroom Croquettes', 'Plain Flour',    40.000),
    ('Mushroom Croquettes', 'Double Cream',   30.000),
    ('Mushroom Croquettes', 'Parmesan',       20.000),

    ('Steak Frites',        'Beef Sirloin',  250.000),
    ('Steak Frites',        'Potato',        300.000),
    ('Steak Frites',        'Butter',         25.000),

    ('Grilled Salmon',      'Salmon Fillet', 180.000),
    ('Grilled Salmon',      'Lemon',           0.500),
    ('Grilled Salmon',      'Olive Oil',      15.000),
    ('Grilled Salmon',      'Mixed Leaves',   60.000),

    ('Chicken Milanese',    'Chicken Breast',220.000),
    ('Chicken Milanese',    'Plain Flour',    50.000),
    ('Chicken Milanese',    'Eggs',            1.000),
    ('Chicken Milanese',    'Parmesan',       25.000),

    ('Mushroom Risotto',    'Mushroom',      150.000),
    ('Mushroom Risotto',    'Onion',          60.000),
    ('Mushroom Risotto',    'Double Cream',   50.000),
    ('Mushroom Risotto',    'Parmesan',       40.000),
    ('Mushroom Risotto',    'Butter',         20.000),

    ('Margherita Pizza',    'Plain Flour',   200.000),
    ('Margherita Pizza',    'Tomato',        120.000),
    ('Margherita Pizza',    'Mozzarella',    110.000),
    ('Margherita Pizza',    'Basil',           4.000),

    ('Bacon Cheeseburger',  'Beef Sirloin',  200.000),
    ('Bacon Cheeseburger',  'Bacon',          45.000),
    ('Bacon Cheeseburger',  'Mozzarella',     40.000),
    ('Bacon Cheeseburger',  'Tomato',         40.000),
    ('Bacon Cheeseburger',  'Onion',          30.000),

    ('Truffle Fries',       'Potato',        250.000),
    ('Truffle Fries',       'Truffle Oil',     8.000),
    ('Truffle Fries',       'Parmesan',       15.000),

    ('Garden Salad',        'Mixed Leaves',   90.000),
    ('Garden Salad',        'Tomato',         60.000),
    ('Garden Salad',        'Olive Oil',      10.000),

    ('Sauteed Mushrooms',   'Mushroom',      140.000),
    ('Sauteed Mushrooms',   'Butter',         20.000),
    ('Sauteed Mushrooms',   'Garlic',          6.000),

    ('Chocolate Fondant',   'Dark Chocolate', 80.000),
    ('Chocolate Fondant',   'Butter',         60.000),
    ('Chocolate Fondant',   'Eggs',            2.000),
    ('Chocolate Fondant',   'Caster Sugar',   50.000),
    ('Chocolate Fondant',   'Plain Flour',    25.000),

    ('Creme Brulee',        'Double Cream',  150.000),
    ('Creme Brulee',        'Eggs',            2.000),
    ('Creme Brulee',        'Caster Sugar',   60.000),
    ('Creme Brulee',        'Vanilla Extract', 5.000),

    ('Lemon Tart',          'Plain Flour',    90.000),
    ('Lemon Tart',          'Butter',         60.000),
    ('Lemon Tart',          'Eggs',            2.000),
    ('Lemon Tart',          'Caster Sugar',   70.000),
    ('Lemon Tart',          'Lemon',           1.500),

    ('Espresso',            'Coffee Beans',   18.000),

    ('Cappuccino',          'Coffee Beans',   18.000),
    ('Cappuccino',          'Milk',          150.000),

    ('Fresh Orange Juice',  'Orange',          3.000),

    ('House Red',           'Red Wine',      175.000),

    ('Bottled Water',       'Bottled Water',   1.000),

    ('Soft Drink',          'Soft Drink Can',  1.000)
) AS v(menu_item, ingredient, quantity)
JOIN menu_item m  ON m.name = v.menu_item
JOIN ingredient i ON i.name = v.ingredient;
