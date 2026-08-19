-- RSCD Community database schema.
--
-- One schema, two tenants: the rscd_* tables belong to the game and login
-- servers (rscd-server), everything else belongs to the community site
-- (rscd-www). There are no foreign keys between the two groups -- the game
-- joins to the site by plain column values (rscd_players.owner is a user.id)
-- -- so a game-only world imports this file exactly the same way a full
-- site-plus-world host does, and simply never touches the site tables.
--
-- Ships with seed data only: the forum boards, the Admin/Member roles and
-- their policies. No accounts. To make your own account an admin once you
-- have registered it, see the README.
--
-- Written for MySQL 5.7. MySQL 8 and MariaDB import it too -- the int(N)
-- display widths and ZEROFILL are deprecated there, but still accepted.

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Table structure for table `contact`
--

CREATE TABLE `contact` (
  `id` int(32) NOT NULL,
  `uuid` varchar(36) DEFAULT NULL,
  `user_id` int(32) DEFAULT NULL,
  `type` varchar(32) DEFAULT NULL,
  `name` varchar(256) DEFAULT NULL,
  `title` varchar(1024) DEFAULT NULL COMMENT '1 KiB',
  `street1` varchar(1024) DEFAULT NULL COMMENT '1 KiB',
  `street2` varchar(1024) DEFAULT NULL COMMENT '1 KiB',
  `street3` varchar(1024) DEFAULT NULL COMMENT '1 KiB',
  `city` varchar(1024) DEFAULT NULL COMMENT '1 KiB',
  `state` varchar(1024) DEFAULT NULL COMMENT '1 KiB',
  `postal_code` varchar(1024) DEFAULT NULL COMMENT '1 KiB',
  `country` varchar(1024) DEFAULT NULL COMMENT '1 KiB',
  `email_address` varchar(256) DEFAULT NULL,
  `phone_number` varchar(256) DEFAULT NULL,
  `mobile_number` varchar(256) DEFAULT NULL,
  `fax_number` varchar(256) DEFAULT NULL,
  `is_default` tinyint(1) DEFAULT '0',
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `contact_event`
--

CREATE TABLE `contact_event` (
  `id` int(32) NOT NULL,
  `contact_id` int(32) DEFAULT NULL,
  `event_id` int(32) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `event`
--

CREATE TABLE `event` (
  `id` int(32) NOT NULL,
  `uuid` varchar(36) DEFAULT NULL,
  `type` tinyint(8) DEFAULT NULL,
  `severity` tinyint(8) DEFAULT NULL,
  `message` text,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `file`
--

CREATE TABLE `file` (
  `id` int(32) NOT NULL,
  `uuid` varchar(36) DEFAULT NULL,
  `user_id` int(32) DEFAULT NULL,
  `secure_email_id` int(32) DEFAULT NULL,
  `name` text,
  `mimetype` text,
  `path` text,
  `size` int(32) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `file_event`
--

CREATE TABLE `file_event` (
  `id` int(11) NOT NULL,
  `file_id` int(32) DEFAULT NULL,
  `event_id` int(32) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `file_metadata`
--

CREATE TABLE `file_metadata` (
  `id` int(32) NOT NULL,
  `file_id` int(32) NOT NULL,
  `metadata_id` int(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `forum_board`
--

CREATE TABLE `forum_board` (
  `id` int(10) UNSIGNED NOT NULL,
  `name` varchar(80) NOT NULL,
  `description` varchar(255) NOT NULL DEFAULT '',
  `sort` int(11) NOT NULL DEFAULT '0',
  `locked` tinyint(1) NOT NULL DEFAULT '0',
  `topics` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `posts` int(10) UNSIGNED NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `forum_board`
--

INSERT INTO `forum_board` (`id`, `name`, `description`, `sort`, `locked`, `topics`, `posts`) VALUES
(1, 'News & Announcements', 'Official news and updates from the RSCD community project. Read all the latest here!', 10, 1, 0, 0),
(2, 'General Discussion', 'Talk about RuneScape Classic and the community.', 20, 0, 0, 0),
(3, 'Introductions', 'New around here? Introduce yourself!', 30, 0, 0, 0),
(4, 'Guides & Tutorials', 'Post your quest guides, skill guides and tutorials here, or ask for help!', 40, 0, 0, 0),
(5, 'Suggestions', 'Have an idea for the game or the site? Post it here.', 50, 0, 0, 0),
(6, 'Support & Bug Reports', 'Problems signing in, account questions, and bug reports.', 60, 0, 0, 0),
(7, 'Community Servers', 'Run a world? Announce and discuss community servers here.', 70, 0, 0, 0),
(8, 'Off Topic', 'Anything and everything else.', 80, 0, 0, 0);

-- --------------------------------------------------------

--
-- Table structure for table `forum_post`
--

CREATE TABLE `forum_post` (
  `id` int(10) UNSIGNED NOT NULL,
  `topic_id` int(10) UNSIGNED NOT NULL,
  `user_id` int(10) UNSIGNED NOT NULL,
  `body` text NOT NULL,
  `created_at` int(10) UNSIGNED NOT NULL,
  `updated_at` int(10) UNSIGNED DEFAULT NULL,
  `edited_by` int(10) UNSIGNED DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `forum_topic`
--

CREATE TABLE `forum_topic` (
  `id` int(10) UNSIGNED NOT NULL,
  `board_id` int(10) UNSIGNED NOT NULL,
  `user_id` int(10) UNSIGNED NOT NULL,
  `title` varchar(120) NOT NULL,
  `locked` tinyint(1) NOT NULL DEFAULT '0',
  `sticky` tinyint(1) NOT NULL DEFAULT '0',
  `posts` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `created_at` int(10) UNSIGNED NOT NULL,
  `last_post_at` int(10) UNSIGNED NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `json_policy`
--

CREATE TABLE `json_policy` (
  `id` int(32) NOT NULL,
  `uuid` varchar(36) NOT NULL,
  `user_id` int(32) DEFAULT NULL,
  `type` tinyint(8) DEFAULT NULL,
  `value` json DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `json_policy`
--

INSERT INTO `json_policy` (`id`, `uuid`, `user_id`, `type`, `value`, `created_at`, `updated_at`) VALUES
(1, 'c698abdc-14b7-11f1-bd33-02038d50ca63', NULL, 1, '{\"name\": \"admin\", \"collections\": [{\"name\": \"admin\", \"rules\": [{\"action\": \"ALLOW\", \"conditions\": [\"%\"]}]}]}', '2023-01-07 15:31:43', '2023-01-07 15:31:43'),
(12, '8ad40d62-1a83-11f1-bd33-02038d50ca63', NULL, 1, '{\"name\": \"member\", \"collections\": [{\"name\": \"member\", \"rules\": []}]}', '2026-03-08 00:12:49', '2026-08-04 12:36:07');

-- --------------------------------------------------------

--
-- Table structure for table `metadata`
--

CREATE TABLE `metadata` (
  `id` int(32) NOT NULL,
  `metakey` varchar(32) NOT NULL,
  `metavalue` text,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `role`
--

CREATE TABLE `role` (
  `id` int(32) NOT NULL,
  `uuid` varchar(36) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `role`
--

INSERT INTO `role` (`id`, `uuid`, `name`, `created_at`, `updated_at`) VALUES
(1, 'c69db193-14b7-11f1-bd33-02038d50ca63', 'Admin', '2023-01-07 15:32:55', '2023-01-07 15:32:55'),
(4, '87e3e936-1a83-11f1-bd33-02038d50ca63', 'Member', '2026-03-08 00:12:44', '2026-03-08 00:12:44');

-- --------------------------------------------------------

--
-- Table structure for table `role_json_policy`
--

CREATE TABLE `role_json_policy` (
  `id` int(32) NOT NULL,
  `role_id` int(32) DEFAULT NULL,
  `json_policy_id` int(32) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `role_json_policy`
--

INSERT INTO `role_json_policy` (`id`, `role_id`, `json_policy_id`) VALUES
(1, 1, 1),
(11, 4, 12);

-- --------------------------------------------------------

--
-- Table structure for table `rscd_bank`
--

CREATE TABLE `rscd_bank` (
  `owner` varchar(255) NOT NULL,
  `id` int(10) UNSIGNED NOT NULL,
  `amount` int(10) UNSIGNED NOT NULL DEFAULT '1',
  `slot` int(5) UNSIGNED NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_curstats`
--

CREATE TABLE `rscd_curstats` (
  `user` varchar(255) NOT NULL,
  `cur_attack` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_defense` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_strength` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_hits` int(5) UNSIGNED NOT NULL DEFAULT '10',
  `cur_ranged` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_prayer` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_magic` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_cooking` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_woodcut` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_fletching` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_fishing` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_firemaking` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_crafting` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_smithing` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_mining` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_herblaw` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_agility` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_thieving` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_runecrafting` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `cur_quest` int(5) UNSIGNED NOT NULL DEFAULT '1',
  `id` int(10) UNSIGNED NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_experience`
--

CREATE TABLE `rscd_experience` (
  `user` varchar(255) NOT NULL,
  `exp_attack` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_defense` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_strength` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_hits` int(10) UNSIGNED NOT NULL DEFAULT '4800',
  `exp_ranged` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_prayer` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_magic` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_cooking` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_woodcut` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_fletching` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_fishing` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_firemaking` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_crafting` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_smithing` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_mining` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_herblaw` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_agility` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_thieving` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_runecrafting` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `exp_quest` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_attack` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_defense` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_strength` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_hits` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_ranged` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_prayer` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_magic` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_cooking` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_woodcut` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_fletching` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_fishing` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_firemaking` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_crafting` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_smithing` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_mining` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_herblaw` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_agility` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_thieving` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `stamp_runecrafting` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `id` int(10) UNSIGNED NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_friends`
--

CREATE TABLE `rscd_friends` (
  `user` varchar(255) NOT NULL,
  `friend` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_ignores`
--

CREATE TABLE `rscd_ignores` (
  `user` varchar(255) NOT NULL,
  `ignore` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_invitems`
--

CREATE TABLE `rscd_invitems` (
  `user` varchar(255) NOT NULL,
  `id` int(10) UNSIGNED NOT NULL,
  `amount` int(10) UNSIGNED NOT NULL DEFAULT '1',
  `wielded` tinyint(1) UNSIGNED NOT NULL DEFAULT '0',
  `slot` int(5) UNSIGNED NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_kills`
--

CREATE TABLE `rscd_kills` (
  `user` varchar(255) NOT NULL DEFAULT '',
  `type` tinyint(1) NOT NULL DEFAULT '0',
  `killed` varchar(45) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_logins`
--

CREATE TABLE `rscd_logins` (
  `id` int(10) UNSIGNED NOT NULL,
  `user` varchar(45) NOT NULL,
  `time` int(5) UNSIGNED NOT NULL,
  `ip` varchar(15) NOT NULL DEFAULT '0.0.0.0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_migrations`
--

CREATE TABLE `rscd_migrations` (
  `name` varchar(255) NOT NULL,
  `applied` int(10) UNSIGNED NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_online`
--

CREATE TABLE `rscd_online` (
  `id` int(10) UNSIGNED NOT NULL,
  `user` varchar(45) NOT NULL,
  `username` varchar(45) NOT NULL,
  `x` varchar(45) NOT NULL,
  `y` varchar(45) NOT NULL,
  `world` int(10) UNSIGNED NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_parties`
--
-- The login server creates this table itself on first use
-- (PartyScheduleHandler), so importing it here is not required. It is
-- included so this file documents every table a running world ends up
-- with. The definition below is a verbatim copy of the handler's DDL --
-- if one changes, change the other.
--

CREATE TABLE IF NOT EXISTS `rscd_parties` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `owner` INT NOT NULL,
  `user` BIGINT NOT NULL,
  `start` BIGINT NOT NULL,
  PRIMARY KEY (`id`), KEY `owner_idx` (`owner`), KEY `start_idx` (`start`)
) ENGINE=InnoDB;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_party_animals`
--
-- Lifetime Party Animals totals: items the party cannons fired during each
-- host's scheduled parties, keyed by character. Kept separately because
-- rscd_parties rows are swept after a day. Created by the login server on
-- first use, same as rscd_parties above and under the same rule: the
-- definition below is a verbatim copy of the handler's DDL -- if one
-- changes, change the other.
--

CREATE TABLE IF NOT EXISTS `rscd_party_animals` (
  `user` BIGINT NOT NULL,
  `items` INT NOT NULL DEFAULT 0,
  `stamp` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`user`)
) ENGINE=InnoDB;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_players`
--

CREATE TABLE `rscd_players` (
  `user` varchar(255) NOT NULL,
  `username` varchar(255) NOT NULL DEFAULT '',
  `group_id` int(10) DEFAULT '4',
  `owner` int(5) UNSIGNED NOT NULL,
  `owner_username` varchar(255) DEFAULT NULL,
  `sub_expires` bigint(255) UNSIGNED DEFAULT '127190145803806',
  `combat` int(10) DEFAULT '3',
  `skill_total` int(10) DEFAULT '3',
  `x` int(11) NOT NULL DEFAULT '122',
  `y` int(11) NOT NULL DEFAULT '647',
  `fatigue` int(10) DEFAULT '0',
  `combatstyle` tinyint(1) DEFAULT '0',
  `block_chat` tinyint(1) UNSIGNED DEFAULT '0',
  `block_private` tinyint(1) UNSIGNED DEFAULT '0',
  `block_trade` tinyint(1) UNSIGNED DEFAULT '0',
  `block_duel` tinyint(1) UNSIGNED DEFAULT '0',
  `cameraauto` tinyint(1) UNSIGNED DEFAULT '0',
  `onemouse` tinyint(1) UNSIGNED DEFAULT '0',
  `soundoff` tinyint(1) UNSIGNED DEFAULT '0',
  `showroof` tinyint(1) DEFAULT '0',
  `autoscreenshot` tinyint(1) DEFAULT '0',
  `combatwindow` tinyint(1) DEFAULT '0',
  `haircolour` int(5) UNSIGNED DEFAULT '2',
  `topcolour` int(5) UNSIGNED DEFAULT '8',
  `trousercolour` int(5) UNSIGNED DEFAULT '14',
  `skincolour` int(5) UNSIGNED DEFAULT '0',
  `headsprite` int(5) UNSIGNED DEFAULT '1',
  `bodysprite` int(5) UNSIGNED DEFAULT '2',
  `male` tinyint(1) UNSIGNED DEFAULT '1',
  `skulled` int(10) UNSIGNED DEFAULT '0',
  `pass` varchar(255) NOT NULL,
  `creation_date` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `creation_ip` varchar(15) NOT NULL DEFAULT '0.0.0.0',
  `login_date` int(10) UNSIGNED DEFAULT '0',
  `login_ip` varchar(15) DEFAULT '0.0.0.0',
  `playermod` tinyint(1) UNSIGNED DEFAULT '0',
  `loggedin` tinyint(1) DEFAULT '0',
  `banned` tinyint(1) DEFAULT '0',
  `muted` tinyint(1) DEFAULT '0',
  `deaths` int(10) DEFAULT '0',
  `god` varchar(150) NOT NULL DEFAULT '75101730521649',
  `god_kills` int(255) NOT NULL DEFAULT '0',
  `id` int(10) UNSIGNED NOT NULL,
  `online` tinyint(1) UNSIGNED ZEROFILL DEFAULT '0',
  `world` int(10) DEFAULT '1',
  `amulet_charges` tinyint(3) UNSIGNED NOT NULL DEFAULT '4',
  `poison_strength` tinyint(3) UNSIGNED NOT NULL DEFAULT '0',
  `poison_hits` tinyint(3) UNSIGNED NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_quests`
--

CREATE TABLE `rscd_quests` (
  `user` varchar(255) NOT NULL,
  `quest` smallint(5) UNSIGNED NOT NULL,
  `stage` int(11) NOT NULL DEFAULT '-1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_reports`
--

CREATE TABLE `rscd_reports` (
  `from` varchar(255) NOT NULL,
  `about` varchar(255) NOT NULL,
  `time` int(10) UNSIGNED NOT NULL,
  `reason` int(5) UNSIGNED NOT NULL,
  `x` int(5) UNSIGNED NOT NULL,
  `y` int(5) UNSIGNED NOT NULL,
  `status` varchar(255) NOT NULL,
  `id` int(255) UNSIGNED NOT NULL,
  `zapped` int(10) UNSIGNED NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_traps`
--

CREATE TABLE `rscd_traps` (
  `user` varchar(255) NOT NULL DEFAULT '',
  `ip` varchar(15) NOT NULL DEFAULT '0.0.0.0',
  `time` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `details` varchar(255) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `rscd_worlds`
--

CREATE TABLE `rscd_worlds` (
  `id` int(10) UNSIGNED NOT NULL,
  `location` varchar(45) NOT NULL,
  `ip` varchar(45) NOT NULL,
  `port` varchar(45) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

-- --------------------------------------------------------

--
-- Table structure for table `session`
--

CREATE TABLE `session` (
  `id` int(32) NOT NULL,
  `uuid` varchar(36) NOT NULL,
  `user_id` int(32) NOT NULL DEFAULT '-1',
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `serial` varchar(32) NOT NULL,
  `ip_address` varchar(45) NOT NULL DEFAULT 'N/a',
  `fingerprint` varchar(64) DEFAULT NULL,
  `is_shop_as` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `session_event`
--

CREATE TABLE `session_event` (
  `id` int(32) NOT NULL,
  `session_id` int(32) DEFAULT NULL,
  `event_id` int(32) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tag`
--

CREATE TABLE `tag` (
  `id` int(32) NOT NULL,
  `uuid` varchar(36) DEFAULT NULL,
  `name` varchar(512) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `user`
--

CREATE TABLE `user` (
  `id` int(32) NOT NULL,
  `uuid` varchar(36) DEFAULT NULL,
  `stripe_uuid_live` varchar(64) DEFAULT NULL,
  `stripe_uuid_test` varchar(64) DEFAULT NULL,
  `parent_id` int(32) DEFAULT NULL,
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `name` varchar(255) DEFAULT NULL,
  `email_address` varchar(256) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `timezone` varchar(64) NOT NULL DEFAULT 'America/New_York',
  `signed_in_last_at` timestamp NULL DEFAULT NULL,
  `password_last_changed_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `user_event`
--

CREATE TABLE `user_event` (
  `id` int(32) NOT NULL,
  `user_id` int(32) DEFAULT NULL,
  `event_id` int(32) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `user_metadata`
--

CREATE TABLE `user_metadata` (
  `id` int(32) NOT NULL,
  `user_id` int(32) NOT NULL,
  `metadata_id` int(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `user_role`
--

CREATE TABLE `user_role` (
  `id` int(32) NOT NULL,
  `user_id` int(32) DEFAULT NULL,
  `role_id` int(32) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `user_tag`
--

CREATE TABLE `user_tag` (
  `id` int(32) NOT NULL,
  `user_id` int(32) DEFAULT NULL,
  `tag_id` int(32) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `contact`
--
ALTER TABLE `contact`
  ADD PRIMARY KEY (`id`),
  ADD KEY `uuid` (`uuid`),
  ADD KEY `user_id` (`user_id`);

--
-- Indexes for table `contact_event`
--
ALTER TABLE `contact_event`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `event`
--
ALTER TABLE `event`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uuid` (`uuid`);

--
-- Indexes for table `file`
--
ALTER TABLE `file`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_uuid` (`uuid`),
  ADD KEY `idx_file_secure_email` (`secure_email_id`),
  ADD KEY `idx_file_user` (`user_id`);

--
-- Indexes for table `file_event`
--
ALTER TABLE `file_event`
  ADD PRIMARY KEY (`id`),
  ADD KEY `file_id` (`file_id`),
  ADD KEY `event_id` (`event_id`);

--
-- Indexes for table `file_metadata`
--
ALTER TABLE `file_metadata`
  ADD PRIMARY KEY (`id`),
  ADD KEY `file_id` (`file_id`),
  ADD KEY `metadata_id` (`metadata_id`);

--
-- Indexes for table `forum_board`
--
ALTER TABLE `forum_board`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `forum_post`
--
ALTER TABLE `forum_post`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_topic` (`topic_id`,`id`);

--
-- Indexes for table `forum_topic`
--
ALTER TABLE `forum_topic`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_board_listing` (`board_id`,`sticky`,`last_post_at`);

--
-- Indexes for table `json_policy`
--
ALTER TABLE `json_policy`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `idx_uuid` (`uuid`);

--
-- Indexes for table `metadata`
--
ALTER TABLE `metadata`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `role`
--
ALTER TABLE `role`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_uuid` (`uuid`);

--
-- Indexes for table `role_json_policy`
--
ALTER TABLE `role_json_policy`
  ADD PRIMARY KEY (`id`),
  ADD KEY `role_id` (`role_id`),
  ADD KEY `json_policy_id` (`json_policy_id`);

--
-- Indexes for table `rscd_curstats`
--
ALTER TABLE `rscd_curstats`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `rscd_experience`
--
ALTER TABLE `rscd_experience`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `rscd_kills`
--
ALTER TABLE `rscd_kills`
  ADD PRIMARY KEY (`user`);

--
-- Indexes for table `rscd_logins`
--
ALTER TABLE `rscd_logins`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `rscd_migrations`
--
ALTER TABLE `rscd_migrations`
  ADD PRIMARY KEY (`name`);

--
-- Indexes for table `rscd_online`
--
ALTER TABLE `rscd_online`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `rscd_players`
--
ALTER TABLE `rscd_players`
  ADD PRIMARY KEY (`id`);
ALTER TABLE `rscd_players` ADD FULLTEXT KEY `user` (`user`);
ALTER TABLE `rscd_players` ADD FULLTEXT KEY `user_2` (`user`);

--
-- Indexes for table `rscd_quests`
--
ALTER TABLE `rscd_quests`
  ADD PRIMARY KEY (`user`,`quest`),
  ADD KEY `quest` (`quest`);

--
-- Indexes for table `rscd_reports`
--
ALTER TABLE `rscd_reports`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `rscd_traps`
--
ALTER TABLE `rscd_traps`
  ADD PRIMARY KEY (`user`);

--
-- Indexes for table `rscd_worlds`
--
ALTER TABLE `rscd_worlds`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `session`
--
ALTER TABLE `session`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `serial` (`serial`),
  ADD KEY `idx_uuid` (`uuid`);

--
-- Indexes for table `session_event`
--
ALTER TABLE `session_event`
  ADD PRIMARY KEY (`id`),
  ADD KEY `session_id` (`session_id`),
  ADD KEY `event_id` (`event_id`);

--
-- Indexes for table `tag`
--
ALTER TABLE `tag`
  ADD PRIMARY KEY (`id`),
  ADD KEY `uuid` (`uuid`);

--
-- Indexes for table `user`
--
ALTER TABLE `user`
  ADD PRIMARY KEY (`id`),
  ADD KEY `stripe_uuid_live` (`stripe_uuid_live`),
  ADD KEY `stripe_uuid_test` (`stripe_uuid_test`),
  ADD KEY `uuid` (`uuid`),
  ADD KEY `parent_id` (`parent_id`);

--
-- Indexes for table `user_event`
--
ALTER TABLE `user_event`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `event_id` (`event_id`);

--
-- Indexes for table `user_metadata`
--
ALTER TABLE `user_metadata`
  ADD PRIMARY KEY (`id`),
  ADD KEY `metadata_id` (`metadata_id`),
  ADD KEY `user_id` (`user_id`);

--
-- Indexes for table `user_role`
--
ALTER TABLE `user_role`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `role_id` (`role_id`);

--
-- Indexes for table `user_tag`
--
ALTER TABLE `user_tag`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `tag_id` (`tag_id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `contact`
--
ALTER TABLE `contact`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `contact_event`
--
ALTER TABLE `contact_event`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `event`
--
ALTER TABLE `event`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `file`
--
ALTER TABLE `file`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- AUTO_INCREMENT for table `file_event`
--
ALTER TABLE `file_event`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `file_metadata`
--
ALTER TABLE `file_metadata`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `forum_board`
--
ALTER TABLE `forum_board`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=9;

--
-- AUTO_INCREMENT for table `forum_post`
--
ALTER TABLE `forum_post`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- AUTO_INCREMENT for table `forum_topic`
--
ALTER TABLE `forum_topic`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- AUTO_INCREMENT for table `json_policy`
--
ALTER TABLE `json_policy`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=13;

--
-- AUTO_INCREMENT for table `metadata`
--
ALTER TABLE `metadata`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- AUTO_INCREMENT for table `role`
--
ALTER TABLE `role`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

--
-- AUTO_INCREMENT for table `role_json_policy`
--
ALTER TABLE `role_json_policy`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=12;

--
-- AUTO_INCREMENT for table `rscd_curstats`
--
ALTER TABLE `rscd_curstats`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- AUTO_INCREMENT for table `rscd_experience`
--
ALTER TABLE `rscd_experience`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- AUTO_INCREMENT for table `rscd_logins`
--
ALTER TABLE `rscd_logins`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `rscd_online`
--
ALTER TABLE `rscd_online`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `rscd_players`
--
ALTER TABLE `rscd_players`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- AUTO_INCREMENT for table `rscd_worlds`
--
ALTER TABLE `rscd_worlds`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- AUTO_INCREMENT for table `session`
--
ALTER TABLE `session`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `session_event`
--
ALTER TABLE `session_event`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tag`
--
ALTER TABLE `tag`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- AUTO_INCREMENT for table `user`
--
ALTER TABLE `user`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- AUTO_INCREMENT for table `user_event`
--
ALTER TABLE `user_event`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `user_metadata`
--
ALTER TABLE `user_metadata`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- AUTO_INCREMENT for table `user_role`
--
ALTER TABLE `user_role`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- AUTO_INCREMENT for table `user_tag`
--
ALTER TABLE `user_tag`
  MODIFY `id` int(32) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
