-- ==========================================
-- EMPLOYEECARE
-- V1 : Gestion des utilisateurs et des rôles
-- ==========================================


-- 1. DEPARTEMENTS

CREATE TABLE departments (

                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                             name VARCHAR(120) NOT NULL UNIQUE,

                             description TEXT,

                             active BOOLEAN NOT NULL DEFAULT TRUE

);


-- 2. ROLES

CREATE TABLE roles (

                       code VARCHAR(30) PRIMARY KEY,

                       description VARCHAR(255)

);


-- 3. UTILISATEURS

CREATE TABLE app_users (

                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                           employee_number VARCHAR(40) UNIQUE,

                           first_name VARCHAR(100) NOT NULL,

                           last_name VARCHAR(100) NOT NULL,

                           email VARCHAR(255) NOT NULL,

                           password_hash VARCHAR(255),

                           phone VARCHAR(30),

                           job_title VARCHAR(150),

                           status VARCHAR(30) NOT NULL
                                               DEFAULT 'EN_ATTENTE_ACTIVATION',

                           department_id UUID,

                           created_at TIMESTAMPTZ NOT NULL
                                               DEFAULT CURRENT_TIMESTAMP,

                           updated_at TIMESTAMPTZ NOT NULL
                                               DEFAULT CURRENT_TIMESTAMP,

                           CONSTRAINT fk_app_users_department
                               FOREIGN KEY (department_id)
                                   REFERENCES departments(id),

                           CONSTRAINT chk_app_users_status
                               CHECK (
                                   status IN (
                                              'ACTIF',
                                              'EN_ATTENTE_ACTIVATION',
                                              'DESACTIVE'
                                       )
                                   )

);


-- 4. EMAIL UNIQUE

CREATE UNIQUE INDEX uq_app_users_email
    ON app_users (LOWER(email));


-- 5. ASSOCIATION UTILISATEURS ET ROLES

CREATE TABLE user_roles (

                            user_id UUID NOT NULL,

                            role_code VARCHAR(30) NOT NULL,

                            PRIMARY KEY (user_id, role_code),

                            CONSTRAINT fk_user_roles_user
                                FOREIGN KEY (user_id)
                                    REFERENCES app_users(id),

                            CONSTRAINT fk_user_roles_role
                                FOREIGN KEY (role_code)
                                    REFERENCES roles(code)

);


-- 6. INSERTION DES ROLES

INSERT INTO roles (code, description)
VALUES

    ('EMPLOYE', 'Utilisateur pouvant signaler ses problèmes'),

    ('RESPONSABLE', 'Utilisateur chargé de traiter les signalements'),

    ('ADMINISTRATEUR', 'Utilisateur chargé de gérer la plateforme');