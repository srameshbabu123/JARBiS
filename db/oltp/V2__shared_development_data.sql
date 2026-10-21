-- Local development accounts only. Password for both: LocalDev123!
-- Negative IDs are reserved for shared fixtures; application identity sequences stay positive.
-- This version runs once. Add a NEW migration to change shared data; do not edit applied files.
INSERT INTO public.users(id, full_name, email, password_hash) VALUES
    (-1001, 'Alice Local', 'alice@example.test', '$2b$10$oYjqramDU/vVNANvKwm2N.VTbQCkVeHvlssLA2pam67S.ZShF9SlS'),
    (-1002, 'Bob Local', 'bob@example.test', '$2b$10$oYjqramDU/vVNANvKwm2N.VTbQCkVeHvlssLA2pam67S.ZShF9SlS');
INSERT INTO public.accounts(id, account_type, currency, balance, owner_id) VALUES
    (-1101, 'INVESTMENT', 'USD', 10000, -1001),
    (-1102, 'INVESTMENT', 'USD', 10000, -1002);
-- Reuse symbols already created by a previous Hibernate startup when adopting an existing volume.
INSERT INTO public.assets(id, asset_type, name)
SELECT -2001, 'STOCK', 'AAPL' WHERE NOT EXISTS (SELECT 1 FROM public.assets WHERE upper(name)='AAPL');
INSERT INTO public.assets(id, asset_type, name)
SELECT -2002, 'STOCK', 'MSFT' WHERE NOT EXISTS (SELECT 1 FROM public.assets WHERE upper(name)='MSFT');
INSERT INTO public.assets(id, asset_type, name)
SELECT -2003, 'STOCK', 'NVDA' WHERE NOT EXISTS (SELECT 1 FROM public.assets WHERE upper(name)='NVDA');
INSERT INTO public.holdings(id, account_id, asset_id, quantity, average_price) VALUES
    (-1201, -1101, (SELECT id FROM public.assets WHERE upper(name)='AAPL' ORDER BY id LIMIT 1), 10, 175),
    (-1202, -1102, (SELECT id FROM public.assets WHERE upper(name)='MSFT' ORDER BY id LIMIT 1), 5, 410);
