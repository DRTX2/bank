# One Fargate service: hardened task definition, least-privilege roles, autoscaling, encrypted logs.
data "aws_region" "current" {}

resource "aws_cloudwatch_log_group" "this" {
  name              = "/ecs/${var.name}"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
}

data "aws_iam_policy_document" "assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# Execution role: what ECS needs to start the task (pull image, write logs, inject secrets).
resource "aws_iam_role" "execution" {
  name               = "${var.name}-execution"
  assume_role_policy = data.aws_iam_policy_document.assume.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "secrets" {
  count = length(var.secrets) > 0 ? 1 : 0
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    # Strip the optional ":json-key::" suffix to get the secret ARN itself.
    resources = distinct([for v in values(var.secrets) : regex("^(arn:[^:]+:secretsmanager:[^:]+:[^:]+:secret:[^:]+)", v)[0]])
  }
  statement {
    actions   = ["kms:Decrypt"]
    resources = [var.kms_key_arn]
  }
}

resource "aws_iam_role_policy" "secrets" {
  count  = length(var.secrets) > 0 ? 1 : 0
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.secrets[0].json
}

# Task role: what the application itself may call (e.g. MSK via IAM).
resource "aws_iam_role" "task" {
  name               = "${var.name}-task"
  assume_role_policy = data.aws_iam_policy_document.assume.json
}

resource "aws_iam_role_policy" "task" {
  count  = var.task_policy_json == null ? 0 : 1
  role   = aws_iam_role.task.id
  policy = var.task_policy_json
}

resource "aws_ecs_task_definition" "this" {
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  volume {
    name = "tmp"
  }

  container_definitions = jsonencode([{
    name                   = var.name
    image                  = var.image
    essential              = true
    user                   = "65532"
    readonlyRootFilesystem = true
    linuxParameters        = { initProcessEnabled = true, capabilities = { drop = ["ALL"] } }
    mountPoints            = [{ sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }]
    portMappings = [for p in var.ports : {
      name          = p.name
      containerPort = p.port
      protocol      = "tcp"
      appProtocol   = p.app_protocol
    }]
    environment = [for k, v in var.environment : { name = k, value = v }]
    secrets     = [for k, v in var.secrets : { name = k, valueFrom = v }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.this.name
        awslogs-region        = data.aws_region.current.region
        awslogs-stream-prefix = var.name
      }
    }
  }])
}

resource "aws_ecs_service" "this" {
  name                               = var.name
  cluster                            = var.cluster_arn
  task_definition                    = aws_ecs_task_definition.this.arn
  desired_count                      = var.min_count
  launch_type                        = "FARGATE"
  propagate_tags                     = "SERVICE"
  enable_execute_command             = false
  health_check_grace_period_seconds  = var.target_group_arn == null ? null : 90
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = var.security_group_ids
    assign_public_ip = false
  }

  dynamic "load_balancer" {
    for_each = var.target_group_arn == null ? [] : [1]
    content {
      target_group_arn = var.target_group_arn
      container_name   = var.name
      container_port   = var.ports[0].port
    }
  }

  # Service Connect: services find each other by name (risk-engine:9090) with built-in retries and metrics.
  service_connect_configuration {
    enabled   = true
    namespace = var.service_connect_namespace_arn
    dynamic "service" {
      for_each = [for p in var.ports : p if p.discoverable]
      content {
        port_name      = service.value.name
        discovery_name = "${var.name}-${service.value.name}"
        client_alias {
          port     = service.value.port
          dns_name = var.name
        }
      }
    }
  }

  lifecycle {
    # CI deploys new images by registering task definitions; Terraform must not roll them back.
    ignore_changes = [task_definition, desired_count]
  }
}

resource "aws_appautoscaling_target" "this" {
  service_namespace  = "ecs"
  resource_id        = "service/${element(split("/", var.cluster_arn), 1)}/${aws_ecs_service.this.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.min_count
  max_capacity       = var.max_count
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${var.name}-cpu"
  service_namespace  = aws_appautoscaling_target.this.service_namespace
  resource_id        = aws_appautoscaling_target.this.resource_id
  scalable_dimension = aws_appautoscaling_target.this.scalable_dimension
  policy_type        = "TargetTrackingScaling"

  target_tracking_scaling_policy_configuration {
    target_value = 60
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
  }
}
