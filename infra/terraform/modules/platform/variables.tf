variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be dev, staging or prod."
  }
}

variable "vpc_cidr" {
  type = string
}

variable "az_count" {
  type    = number
  default = 3
}

variable "single_nat_gateway" {
  type    = bool
  default = false
}

variable "deletion_protection" {
  type    = bool
  default = true
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.medium"
}

variable "db_multi_az" {
  type    = bool
  default = true
}

variable "db_backup_retention_days" {
  type    = number
  default = 14
}

variable "access_log_retention_days" {
  type    = number
  default = 400
}

variable "image_tag" {
  description = "Initial image tag; later deployments come from CI."
  type        = string
  default     = "bootstrap"
}

variable "core_cpu" {
  type    = number
  default = 1024
}

variable "core_memory" {
  type    = number
  default = 2048
}

variable "min_tasks" {
  type    = number
  default = 2
}

variable "max_tasks" {
  type    = number
  default = 8
}

variable "domain_name" {
  description = "Public hostname of the web app, e.g. app.kuntur.dev"
  type        = string
}

variable "api_origin_domain" {
  description = "Hostname (CNAME to the ALB) covered by alb_certificate_arn, e.g. origin.kuntur.dev"
  type        = string
}

variable "alb_certificate_arn" {
  type = string
}

variable "cloudfront_certificate_arn" {
  type = string
}

variable "github_repository" {
  type = string
}

variable "create_github_oidc_provider" {
  type    = bool
  default = true
}

variable "alarm_email" {
  type    = string
  default = null
}
