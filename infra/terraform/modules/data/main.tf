# PostgreSQL (RDS) for both services (one database each, separate users) and MSK Serverless for events.
resource "aws_db_subnet_group" "this" {
  name       = "${var.name}-db"
  subnet_ids = var.data_subnet_ids
}

resource "aws_security_group" "db" {
  name        = "${var.name}-db"
  description = "PostgreSQL reachable only from application tasks"
  vpc_id      = var.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "db_from_app" {
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = var.app_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "PostgreSQL from ECS tasks"
}

resource "aws_db_parameter_group" "this" {
  name   = "${var.name}-pg17"
  family = "postgres17"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
  parameter {
    name  = "log_connections"
    value = "1"
  }
  parameter {
    name  = "log_disconnections"
    value = "1"
  }
  parameter {
    name  = "log_min_duration_statement"
    value = "500"
  }
}

data "aws_iam_policy_document" "monitoring_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["monitoring.rds.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "monitoring" {
  name               = "${var.name}-rds-monitoring"
  assume_role_policy = data.aws_iam_policy_document.monitoring_assume.json
}

resource "aws_iam_role_policy_attachment" "monitoring" {
  role       = aws_iam_role.monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

resource "aws_db_instance" "this" {
  identifier     = "${var.name}-postgres"
  engine         = "postgres"
  engine_version = "17"
  instance_class = var.instance_class

  db_name  = "kuntur"
  username = "kuntur_admin"
  # Master password generated and rotated by RDS inside Secrets Manager: it never appears in state or code.
  manage_master_user_password   = true
  master_user_secret_kms_key_id = var.kms_key_arn

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.allocated_storage * 5
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = var.kms_key_arn

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false
  multi_az               = var.multi_az

  iam_database_authentication_enabled = true
  backup_retention_period             = var.backup_retention_days
  backup_window                       = "07:00-08:00"
  maintenance_window                  = "sun:08:30-sun:09:30"
  copy_tags_to_snapshot               = true
  deletion_protection                 = var.deletion_protection
  skip_final_snapshot                 = !var.deletion_protection
  final_snapshot_identifier           = var.deletion_protection ? "${var.name}-final" : null
  auto_minor_version_upgrade          = true

  performance_insights_enabled          = true
  performance_insights_kms_key_id       = var.kms_key_arn
  performance_insights_retention_period = 7
  monitoring_interval                   = 60
  monitoring_role_arn                   = aws_iam_role.monitoring.arn
  enabled_cloudwatch_logs_exports       = ["postgresql", "upgrade"]
}

resource "aws_security_group" "kafka" {
  name        = "${var.name}-msk"
  description = "MSK Serverless reachable only from application tasks"
  vpc_id      = var.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "kafka_from_app" {
  security_group_id            = aws_security_group.kafka.id
  referenced_security_group_id = var.app_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 9098
  to_port                      = 9098
  description                  = "Kafka IAM/TLS from ECS tasks"
}

# Serverless: no brokers to size or patch; TLS + IAM auth are mandatory.
resource "aws_msk_serverless_cluster" "this" {
  cluster_name = "${var.name}-events"

  vpc_config {
    subnet_ids         = var.data_subnet_ids
    security_group_ids = [aws_security_group.kafka.id]
  }

  client_authentication {
    sasl {
      iam {
        enabled = true
      }
    }
  }
}
