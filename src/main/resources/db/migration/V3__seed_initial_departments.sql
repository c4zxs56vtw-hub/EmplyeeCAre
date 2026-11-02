INSERT INTO departments (
    id,
    name,
    active
)
SELECT
    gen_random_uuid(),
    'Administration',
    TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM departments
    WHERE name = 'Administration'
);
