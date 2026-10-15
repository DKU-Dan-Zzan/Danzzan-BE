-- Azure demo bootstrap schema for the isolated danzzan_demo database.
-- Source: schema-only local MySQL 8.4 dump from the current application-validated schema.
-- It intentionally contains no row data, triggers, routines, events, definers, or DROP TABLE.
-- MySQL Docker init must select MYSQL_DATABASE=danzzan_demo before executing this file.

DELIMITER //
CREATE PROCEDURE assert_danzzan_demo_schema_target()
BEGIN
    IF DATABASE() <> 'danzzan_demo' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Refusing schema bootstrap: selected database must be danzzan_demo';
    END IF;
END//
CALL assert_danzzan_demo_schema_target()//
DROP PROCEDURE assert_danzzan_demo_schema_target//
DELIMITER ;


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `advertisement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `end_date` datetime(6) DEFAULT NULL,
  `image_url` varchar(2048) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `object_position` varchar(32) DEFAULT NULL,
  `placement` enum('HOME_BOTTOM','MY_TICKET') NOT NULL,
  `title` varchar(255) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `link_url` varchar(2048) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `artist` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `image_url` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `name_en` varchar(512) DEFAULT NULL,
  `description_en` text,
  `en_is_manual` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booth` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `image_url` varchar(255) DEFAULT NULL,
  `location_x` double DEFAULT NULL,
  `location_y` double DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `type` enum('EVENT','EXPERIENCE','FACILITY','FOOD_TRUCK','REST_AREA') NOT NULL,
  `name_en` varchar(512) DEFAULT NULL,
  `description_en` text,
  `en_is_manual` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booth_operation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `end_time` time(6) NOT NULL,
  `operation_date` date NOT NULL,
  `start_time` time(6) NOT NULL,
  `booth_id` bigint NOT NULL,
  `operation_status` enum('CLOSED','OPEN','UNKNOWN') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_booth_operation_booth_date` (`booth_id`,`operation_date`),
  CONSTRAINT `FK3kyomlymmqgk7e4q0w3bocohn` FOREIGN KEY (`booth_id`) REFERENCES `booth` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `college` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `location_x` double NOT NULL,
  `location_y` double NOT NULL,
  `name` varchar(255) NOT NULL,
  `name_en` varchar(512) DEFAULT NULL,
  `en_is_manual` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `content_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `detail_image_url` varchar(255) NOT NULL,
  `display_order` int NOT NULL,
  `name` varchar(255) NOT NULL,
  `preview_image_url` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `emergency_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `message` varchar(500) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `message_en` text,
  `en_is_manual` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `festival_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_date` date NOT NULL,
  `ticketing_start_time` datetime(6) NOT NULL,
  `ticketing_status` enum('CLOSED','OPEN','READY') NOT NULL,
  `title` varchar(255) NOT NULL,
  `total_capacity` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `festival_map_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active_operation_date` date NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `festival_setting` (
  `id` bigint NOT NULL,
  `school_name` varchar(100) NOT NULL,
  `festival_name` varchar(255) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `ticketing_enabled` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `ticket_card_background_image_url` varchar(2048) DEFAULT NULL,
  `ticketing_background_image_url` varchar(2048) DEFAULT NULL,
  `active_theme_id` bigint DEFAULT NULL,
  `poster_image_url` varchar(2048) DEFAULT NULL,
  `logo_image_url` varchar(2048) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_festival_setting_active_theme` (`id`,`active_theme_id`),
  CONSTRAINT `fk_festival_setting_active_theme` FOREIGN KEY (`id`, `active_theme_id`) REFERENCES `theme` (`festival_setting_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `festival_ticketing_round` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ticketing_at` datetime(6) NOT NULL,
  `capacity` int NOT NULL,
  `performance_date` date NOT NULL,
  `display_order` int NOT NULL,
  `event_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `home_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `display_order` int NOT NULL,
  `image_url` varchar(255) NOT NULL,
  `festival_setting_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_home_image_festival_display` (`festival_setting_id`,`display_order`),
  CONSTRAINT `fk_home_image_festival_setting` FOREIGN KEY (`festival_setting_id`) REFERENCES `festival_setting` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lineup_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `display_order` int NOT NULL,
  `image_url` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author` varchar(255) NOT NULL,
  `category` varchar(50) DEFAULT NULL,
  `content` text NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `display_order` int NOT NULL,
  `is_active` bit(1) NOT NULL,
  `is_emergency` bit(1) NOT NULL,
  `is_pinned` bit(1) DEFAULT NULL,
  `thumbnail_image_url` varchar(2048) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `title_en` varchar(512) DEFAULT NULL,
  `content_en` text,
  `en_is_manual` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notice_image` (
  `notice_id` bigint NOT NULL,
  `image_url` varchar(2048) NOT NULL,
  `position` int NOT NULL,
  PRIMARY KEY (`notice_id`,`position`),
  CONSTRAINT `FK13h2d99jqpw7xnaj5w1crvv8m` FOREIGN KEY (`notice_id`) REFERENCES `notice` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `outbox_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aggregate_id` varchar(100) NOT NULL,
  `aggregate_type` varchar(50) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `event_key` varchar(100) NOT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `payload` tinytext NOT NULL,
  `retry_count` int NOT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `status` enum('FAILED','PENDING','SENT') NOT NULL,
  `topic` varchar(200) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_aggregate` (`aggregate_type`,`aggregate_id`),
  KEY `idx_outbox_status_next_retry` (`status`,`next_retry_at`),
  KEY `idx_outbox_aggregate` (`aggregate_type`,`aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `performance` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `end_time` time(6) NOT NULL,
  `performance_date` date NOT NULL,
  `stage` varchar(255) DEFAULT NULL,
  `start_time` time(6) NOT NULL,
  `artist_id` int NOT NULL,
  `stage_en` varchar(512) DEFAULT NULL,
  `en_is_manual` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `FKuatgv2wsnmbmb4gesus15x59` (`artist_id`),
  CONSTRAINT `FKuatgv2wsnmbmb4gesus15x59` FOREIGN KEY (`artist_id`) REFERENCES `artist` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `phone_verification_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempt_count` int NOT NULL,
  `code_ciphertext` varchar(512) NOT NULL,
  `code_hash` varchar(128) NOT NULL,
  `consumed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `last_checked_at` datetime(6) DEFAULT NULL,
  `request_ip_hash` varchar(128) DEFAULT NULL,
  `session_id` varchar(64) NOT NULL,
  `signup_token` varchar(64) NOT NULL,
  `status` enum('CONSUMED','EXPIRED','FAILED','PENDING','VERIFIED') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `verified_phone_number` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone_verification_session_session_id` (`session_id`),
  KEY `idx_phone_verification_session_signup_token` (`signup_token`),
  KEY `idx_phone_verification_session_status_expires_at` (`status`,`expires_at`),
  KEY `idx_phone_verification_session_request_ip_hash_created_at` (`request_ip_hash`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pub` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `department` varchar(255) NOT NULL,
  `description` text,
  `instagram` varchar(255) DEFAULT NULL,
  `intro` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `college_id` bigint NOT NULL,
  `name_en` varchar(512) DEFAULT NULL,
  `intro_en` varchar(1000) DEFAULT NULL,
  `description_en` text,
  `department_en` varchar(512) DEFAULT NULL,
  `en_is_manual` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `FK94ustbkghug9mtowpre4uhx5i` (`college_id`),
  CONSTRAINT `FK94ustbkghug9mtowpre4uhx5i` FOREIGN KEY (`college_id`) REFERENCES `college` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pub_display_day` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pub_id` bigint NOT NULL,
  `pub_operation_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_pub_display_day` (`pub_id`,`pub_operation_id`),
  KEY `fk_pub_display_day_operation` (`pub_operation_id`),
  CONSTRAINT `fk_pub_display_day_operation` FOREIGN KEY (`pub_operation_id`) REFERENCES `pub_operation` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pub_display_day_pub` FOREIGN KEY (`pub_id`) REFERENCES `pub` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pub_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `image_url` varchar(255) NOT NULL,
  `is_main` bit(1) NOT NULL,
  `pub_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmv9vohe78ttaqxapwajn8slxy` (`pub_id`),
  CONSTRAINT `FKmv9vohe78ttaqxapwajn8slxy` FOREIGN KEY (`pub_id`) REFERENCES `pub` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pub_operation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `end_time` time(6) NOT NULL,
  `operation_date` date NOT NULL,
  `start_time` time(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pub_operation_date` (`operation_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `theme` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `festival_setting_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `theme_data` json NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `source_poster_url` varchar(2048) DEFAULT NULL,
  `selected_base_color` varchar(7) DEFAULT NULL,
  `source_colors` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_theme_festival_setting_id` (`festival_setting_id`,`id`),
  CONSTRAINT `fk_theme_festival_setting` FOREIGN KEY (`festival_setting_id`) REFERENCES `festival_setting` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ticket_issue_compensation_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempt` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `error_reason` varchar(500) DEFAULT NULL,
  `event_id` bigint NOT NULL,
  `request_id` varchar(36) NOT NULL,
  `rollback_result` varchar(30) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ticket_issue_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `compensated` bit(1) NOT NULL,
  `compensated_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `error_code` varchar(100) DEFAULT NULL,
  `error_reason` varchar(500) DEFAULT NULL,
  `event_id` bigint NOT NULL,
  `request_id` varchar(36) NOT NULL,
  `status` enum('FAILED','PROCESSING','SUCCESS') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `compensation_attempts` int NOT NULL,
  `compensation_pending` bit(1) NOT NULL,
  `accepted_at` bigint DEFAULT NULL,
  `remaining_after_claim` bigint DEFAULT NULL,
  `seq` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ticket_issue_request_id` (`request_id`),
  UNIQUE KEY `uk_ticket_issue_event_user` (`event_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ticket_queue_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entered_at` datetime(6) DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `ready_until` datetime(6) DEFAULT NULL,
  `seq` bigint DEFAULT NULL,
  `status` enum('ACTIVE','CANCELLED','DONE','EXPIRED','FAILED','READY','WAITING') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `event_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_queue_entry_event_user` (`event_id`,`user_id`),
  KEY `FKhpefar2tsogtksxiiycqnko6k` (`user_id`),
  CONSTRAINT `FK1xl5wa2bcij6cdo8g3f145i6m` FOREIGN KEY (`event_id`) REFERENCES `festival_events` (`id`),
  CONSTRAINT `FKhpefar2tsogtksxiiycqnko6k` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `timetable_display_setting` (
  `id` bigint NOT NULL,
  `coming_soon_overlay_enabled` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_role_change_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_user_id` bigint NOT NULL,
  `target_user_id` bigint NOT NULL,
  `previous_role` varchar(32) NOT NULL,
  `new_role` varchar(32) NOT NULL,
  `changed_at` datetime(6) NOT NULL,
  `previous_permissions` varchar(64) DEFAULT NULL,
  `new_permissions` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_role_history_target_time` (`target_user_id`,`changed_at`),
  KEY `idx_user_role_change_history_target_changed` (`target_user_id`,`changed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_tickets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `issued_at` datetime(6) DEFAULT NULL,
  `seq` bigint DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `ticketing_at` datetime(6) NOT NULL,
  `ticketing_order` int NOT NULL,
  `event_id` bigint NOT NULL,
  `issuer_admin_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `cancel_reason` varchar(50) DEFAULT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_event` (`user_id`,`event_id`),
  KEY `FKo23tdmwilaxiupjsrhhd2mwxc` (`event_id`),
  KEY `FKc9w9psfijnjxri30194rq780i` (`issuer_admin_id`),
  CONSTRAINT `FK9ok1dlkt49it7wyeh529wy315` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKc9w9psfijnjxri30194rq780i` FOREIGN KEY (`issuer_admin_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKo23tdmwilaxiupjsrhhd2mwxc` FOREIGN KEY (`event_id`) REFERENCES `festival_events` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `academic_status` enum('COMPLETED','ENROLLED','ENROLLED_LEAVE','GRADUATED','GRADUATION_DEFERRED','LEAVE') NOT NULL,
  `college` varchar(255) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `major` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` enum('ROLE_ADMIN','ROLE_USER','ROLE_MANAGER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `student_id` varchar(255) NOT NULL,
  `token_version` int NOT NULL DEFAULT '0',
  `naver_id` varchar(100) DEFAULT NULL,
  `phone_number` varchar(32) DEFAULT NULL,
  `is_phone_verified` bit(1) NOT NULL,
  `phone_verified_at` datetime(6) DEFAULT NULL,
  `is_deleted` bit(1) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `manager_operations` bit(1) NOT NULL DEFAULT b'0',
  `manager_ticketing` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqh3otyipv2k9hqte4a1abcyhq` (`student_id`),
  UNIQUE KEY `UK9q63snka3mdh91as4io72espi` (`phone_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
