variable "region" {
  type = string
}

variable "environment" {
  type = string
}

variable "vpc_cidr" {
  type = string
}

variable "az_count" {
  type = number
}

variable "single_nat_gateway" {
  type = bool
}

variable "deletion_protection" {
  type = bool
}

variable "db_instance_class" {
  type = string
}

variable "db_multi_az" {
  type = bool
}

variable "min_tasks" {
  type = number
}

variable "max_tasks" {
  type = number
}

variable "domain_name" {
  type = string
}

variable "api_origin_domain" {
  type = string
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
  type = bool
}

variable "alarm_email" {
  type    = string
  default = null
}
