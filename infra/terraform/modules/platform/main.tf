# Composition of the whole platform for one environment. Environments only differ in variables.
data "aws_caller_identity" "current" {}
data "aws_region" "current" {}
data "aws_elb_service_account" "current" {}

locals {
  name       = "kuntur-${var.environment}"
  account_id = data.aws_caller_identity.current.account_id
  region     = data.aws_region.current.region
}

# ---------------------------------------------------------------- encryption

data "aws_iam_policy_document" "kms" {
  statement {
    sid       = "AccountAdministration"
    actions   = ["kms:*"]
    resources = ["*"]
    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${local.account_id}:root"]
    }
  }
  statement {
    sid       = "CloudFrontReadsSiteBucket"
    actions   = ["kms:Decrypt"]
    resources = ["*"]
    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceAccount"
      values   = [local.account_id]
    }
  }
  statement {
    sid       = "CloudWatchLogs"
    actions   = ["kms:Encrypt*", "kms:Decrypt*", "kms:ReEncrypt*", "kms:GenerateDataKey*", "kms:Describe*"]
    resources = ["*"]
    principals {
      type        = "Service"
      identifiers = ["logs.${local.region}.amazonaws.com"]
    }
    condition {
      test     = "ArnLike"
      variable = "kms:EncryptionContext:aws:logs:arn"
      values   = ["arn:aws:logs:${local.region}:${local.account_id}:*"]
    }
  }
}

resource "aws_kms_key" "this" {
  description             = "${local.name}: data at rest (RDS, logs, secrets, ECR)"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  policy                  = data.aws_iam_policy_document.kms.json
}

resource "aws_kms_alias" "this" {
  name          = "alias/${local.name}"
  target_key_id = aws_kms_key.this.key_id
}

# ---------------------------------------------------------------- network

module "network" {
  source             = "../network"
  name               = local.name
  region             = local.region
  cidr               = var.vpc_cidr
  az_count           = var.az_count
  single_nat_gateway = var.single_nat_gateway
  kms_key_arn        = aws_kms_key.this.arn
}

# ---------------------------------------------------------------- logs bucket (ALB + CloudFront access logs)

resource "aws_s3_bucket" "logs" {
  bucket        = "${local.name}-logs-${local.account_id}"
  force_destroy = !var.deletion_protection
}

resource "aws_s3_bucket_public_access_block" "logs" {
  bucket                  = aws_s3_bucket.logs.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# CloudFront standard logging still writes through ACLs.
resource "aws_s3_bucket_ownership_controls" "logs" {
  bucket = aws_s3_bucket.logs.id
  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

#trivy:ignore:AVD-AWS-0132 ALB access logs only support SSE-S3 (AES256), not customer-managed KMS keys.
resource "aws_s3_bucket_server_side_encryption_configuration" "logs" {
  bucket = aws_s3_bucket.logs.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256" # ALB access logs only support SSE-S3
    }
  }
}

resource "aws_s3_bucket_versioning" "logs" {
  bucket = aws_s3_bucket.logs.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "logs" {
  bucket = aws_s3_bucket.logs.id
  rule {
    id     = "retention"
    status = "Enabled"
    filter {}
    transition {
      days          = 90
      storage_class = "GLACIER_IR"
    }
    expiration {
      days = var.access_log_retention_days
    }
    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}

data "aws_iam_policy_document" "logs" {
  statement {
    sid       = "AlbAccessLogs"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.logs.arn}/alb/AWSLogs/${local.account_id}/*"]
    principals {
      type        = "AWS"
      identifiers = [data.aws_elb_service_account.current.arn]
    }
  }
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.logs.arn, "${aws_s3_bucket.logs.arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "logs" {
  bucket = aws_s3_bucket.logs.id
  policy = data.aws_iam_policy_document.logs.json
}

# ---------------------------------------------------------------- container registry & cluster

resource "aws_ecr_repository" "this" {
  for_each             = toset(["core-banking", "risk-engine"])
  name                 = "kuntur/${each.key}"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = !var.deletion_protection

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = aws_kms_key.this.arn
  }
}

resource "aws_ecr_lifecycle_policy" "this" {
  for_each   = aws_ecr_repository.this
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 30 images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 30 }
      action       = { type = "expire" }
    }]
  })
}

resource "aws_ecs_cluster" "this" {
  name = local.name
  setting {
    name  = "containerInsights"
    value = "enhanced"
  }
}

resource "aws_service_discovery_http_namespace" "this" {
  name = "${local.name}.internal"
}

# ---------------------------------------------------------------- application security group

resource "aws_security_group" "app" {
  name        = "${local.name}-app"
  description = "ECS tasks of core-banking and risk-engine"
  vpc_id      = module.network.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "app_from_alb" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = module.edge.alb_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  description                  = "API traffic from the ALB"
}

resource "aws_vpc_security_group_ingress_rule" "app_grpc" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.app.id
  ip_protocol                  = "tcp"
  from_port                    = 9090
  to_port                      = 9090
  description                  = "gRPC core-banking -> risk-engine"
}

# No internet egress for application tasks: AWS APIs are reached through VPC endpoints only.
resource "aws_vpc_security_group_egress_rule" "app_endpoints" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = module.network.vpc_cidr
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  description       = "Interface VPC endpoints (ECR, Logs, Secrets Manager, KMS)"
}

data "aws_ec2_managed_prefix_list" "s3" {
  name = "com.amazonaws.${local.region}.s3"
}

resource "aws_vpc_security_group_egress_rule" "app_s3" {
  security_group_id = aws_security_group.app.id
  prefix_list_id    = data.aws_ec2_managed_prefix_list.s3.id
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  description       = "S3 gateway endpoint (ECR image layers)"
}

resource "aws_vpc_security_group_egress_rule" "app_internal" {
  for_each          = { postgres = 5432, kafka = 9098, grpc = 9090 }
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = module.network.vpc_cidr
  ip_protocol       = "tcp"
  from_port         = each.value
  to_port           = each.value
  description       = "${each.key} inside the VPC"
}

# ---------------------------------------------------------------- data

module "data" {
  source                = "../data"
  name                  = local.name
  vpc_id                = module.network.vpc_id
  data_subnet_ids       = module.network.data_subnet_ids
  app_security_group_id = aws_security_group.app.id
  kms_key_arn           = aws_kms_key.this.arn
  instance_class        = var.db_instance_class
  multi_az              = var.db_multi_az
  deletion_protection   = var.deletion_protection
  backup_retention_days = var.db_backup_retention_days
}

# ---------------------------------------------------------------- secrets (values are set out of band, never in Terraform)

resource "aws_secretsmanager_secret" "app" {
  for_each                = toset(["jwt-signing-key", "mfa-seed-key", "core-banking-db", "risk-engine-db"])
  name                    = "${local.name}/${each.key}"
  kms_key_id              = aws_kms_key.this.arn
  recovery_window_in_days = var.deletion_protection ? 30 : 0
}

resource "random_password" "origin_verify" {
  length  = 48
  special = false
}

# ---------------------------------------------------------------- services

locals {
  kafka_topic_arn = "${replace(module.data.kafka_cluster_arn, ":cluster/", ":topic/")}/bank.payments.*"
  kafka_group_arn = "${replace(module.data.kafka_cluster_arn, ":cluster/", ":group/")}/*"
  common_env = {
    SPRING_PROFILES_ACTIVE = "aws"
    KAFKA_BOOTSTRAP        = module.data.kafka_bootstrap_brokers
    LOG_FORMAT             = "ecs"
  }
}

data "aws_iam_policy_document" "kafka" {
  statement {
    actions   = ["kafka-cluster:Connect", "kafka-cluster:DescribeCluster"]
    resources = [module.data.kafka_cluster_arn]
  }
  statement {
    actions = ["kafka-cluster:DescribeTopic", "kafka-cluster:CreateTopic", "kafka-cluster:WriteData",
    "kafka-cluster:ReadData"]
    resources = [local.kafka_topic_arn]
  }
  statement {
    actions   = ["kafka-cluster:AlterGroup", "kafka-cluster:DescribeGroup"]
    resources = [local.kafka_group_arn]
  }
}

module "core_banking" {
  source                        = "../ecs-service"
  name                          = "core-banking"
  image                         = "${aws_ecr_repository.this["core-banking"].repository_url}:${var.image_tag}"
  cluster_arn                   = aws_ecs_cluster.this.arn
  service_connect_namespace_arn = aws_service_discovery_http_namespace.this.arn
  cpu                           = var.core_cpu
  memory                        = var.core_memory
  ports                         = [{ name = "http", port = 8080, app_protocol = "http", discoverable = false }]
  subnet_ids                    = module.network.private_subnet_ids
  security_group_ids            = [aws_security_group.app.id]
  target_group_arn              = module.edge.target_group_arn
  task_policy_json              = data.aws_iam_policy_document.kafka.json
  min_count                     = var.min_tasks
  max_count                     = var.max_tasks
  kms_key_arn                   = aws_kms_key.this.arn
  environment = merge(local.common_env, {
    DB_URL             = "jdbc:postgresql://${module.data.db_endpoint}:5432/kuntur?sslmode=require"
    RISK_ENGINE_TARGET = "risk-engine:9090"
    CORS_ORIGINS       = "https://${var.domain_name}"
    API_DOCS_ENABLED   = "false"
    TRACING_EXPORT     = "false"
  })
  secrets = {
    DB_USER         = "${aws_secretsmanager_secret.app["core-banking-db"].arn}:username::"
    DB_PASSWORD     = "${aws_secretsmanager_secret.app["core-banking-db"].arn}:password::"
    JWT_PRIVATE_KEY = "${aws_secretsmanager_secret.app["jwt-signing-key"].arn}:private::"
    JWT_PUBLIC_KEY  = "${aws_secretsmanager_secret.app["jwt-signing-key"].arn}:public::"
    MFA_SEED_KEY    = aws_secretsmanager_secret.app["mfa-seed-key"].arn # base64, 32 random bytes
  }
}

module "risk_engine" {
  source                        = "../ecs-service"
  name                          = "risk-engine"
  image                         = "${aws_ecr_repository.this["risk-engine"].repository_url}:${var.image_tag}"
  cluster_arn                   = aws_ecs_cluster.this.arn
  service_connect_namespace_arn = aws_service_discovery_http_namespace.this.arn
  ports = [
    { name = "grpc", port = 9090, app_protocol = "grpc", discoverable = true },
    { name = "management", port = 8081, app_protocol = "http", discoverable = false },
  ]
  subnet_ids         = module.network.private_subnet_ids
  security_group_ids = [aws_security_group.app.id]
  task_policy_json   = data.aws_iam_policy_document.kafka.json
  min_count          = var.min_tasks
  max_count          = var.max_tasks
  kms_key_arn        = aws_kms_key.this.arn
  environment = merge(local.common_env, {
    DB_URL = "jdbc:postgresql://${module.data.db_endpoint}:5432/risk?sslmode=require"
  })
  secrets = {
    DB_USER     = "${aws_secretsmanager_secret.app["risk-engine-db"].arn}:username::"
    DB_PASSWORD = "${aws_secretsmanager_secret.app["risk-engine-db"].arn}:password::"
  }
}

# ---------------------------------------------------------------- edge

module "edge" {
  source                = "../edge"
  name                  = local.name
  vpc_id                = module.network.vpc_id
  public_subnet_ids     = module.network.public_subnet_ids
  app_security_group_id = aws_security_group.app.id
  certificate_arn       = var.alb_certificate_arn
  access_logs_bucket    = aws_s3_bucket.logs.bucket
  origin_verify_secret  = random_password.origin_verify.result
  deletion_protection   = var.deletion_protection
  kms_key_arn           = aws_kms_key.this.arn
}

module "web" {
  source                    = "../static-site"
  name                      = local.name
  account_id                = local.account_id
  domain_name               = var.domain_name
  certificate_arn_us_east_1 = var.cloudfront_certificate_arn
  api_origin_domain         = var.api_origin_domain
  origin_verify_secret      = random_password.origin_verify.result
  logs_bucket               = aws_s3_bucket.logs.bucket
  kms_key_arn               = aws_kms_key.this.arn
}

module "github" {
  source              = "../github-oidc"
  name                = local.name
  create_provider     = var.create_github_oidc_provider
  github_repository   = var.github_repository
  github_environment  = var.environment
  ecr_repository_arns = [for r in aws_ecr_repository.this : r.arn]
  ecs_service_arns = [
    "arn:aws:ecs:${local.region}:${local.account_id}:service/${aws_ecs_cluster.this.name}/core-banking",
    "arn:aws:ecs:${local.region}:${local.account_id}:service/${aws_ecs_cluster.this.name}/risk-engine",
  ]
  task_role_arns = [module.core_banking.task_role_arn, module.core_banking.execution_role_arn,
  module.risk_engine.task_role_arn, module.risk_engine.execution_role_arn]
  site_bucket_arn  = module.web.bucket_arn
  distribution_arn = module.web.distribution_arn
}

# ---------------------------------------------------------------- alarms

resource "aws_sns_topic" "alarms" {
  name              = "${local.name}-alarms"
  kms_master_key_id = aws_kms_key.this.id
}

resource "aws_sns_topic_subscription" "email" {
  count     = var.alarm_email == null ? 0 : 1
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

locals {
  alarms = {
    api-5xx = {
      namespace = "AWS/ApplicationELB", metric = "HTTPCode_Target_5XX_Count", stat = "Sum", threshold = 10,
      dims      = { LoadBalancer = module.edge.alb_arn_suffix, TargetGroup = module.edge.target_group_arn_suffix }
    }
    api-latency-p95 = {
      namespace = "AWS/ApplicationELB", metric = "TargetResponseTime", stat = "p95", threshold = 1,
      dims      = { LoadBalancer = module.edge.alb_arn_suffix, TargetGroup = module.edge.target_group_arn_suffix }
    }
    db-cpu = {
      namespace = "AWS/RDS", metric = "CPUUtilization", stat = "Average", threshold = 80,
      dims      = { DBInstanceIdentifier = module.data.db_instance_id }
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "this" {
  for_each            = local.alarms
  alarm_name          = "${local.name}-${each.key}"
  namespace           = each.value.namespace
  metric_name         = each.value.metric
  dimensions          = each.value.dims
  statistic           = each.value.stat == "p95" ? null : each.value.stat
  extended_statistic  = each.value.stat == "p95" ? "p95" : null
  period              = 300
  evaluation_periods  = 2
  threshold           = each.value.threshold
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
}

resource "aws_cloudwatch_metric_alarm" "db_storage" {
  alarm_name          = "${local.name}-db-free-storage"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  dimensions          = { DBInstanceIdentifier = module.data.db_instance_id }
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 5 * 1024 * 1024 * 1024
  comparison_operator = "LessThanThreshold"
  alarm_actions       = [aws_sns_topic.alarms.arn]
}
