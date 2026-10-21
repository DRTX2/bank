variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "app_security_group_id" {
  type = string
}

variable "target_port" {
  type    = number
  default = 8080
}

variable "certificate_arn" {
  description = "ACM certificate for the ALB (regional)."
  type        = string
}

variable "access_logs_bucket" {
  type = string
}

variable "origin_verify_secret" {
  description = "Shared secret CloudFront sends as X-Origin-Verify."
  type        = string
  sensitive   = true
}

variable "login_rate_limit_per_5min" {
  type    = number
  default = 100
}

variable "deletion_protection" {
  type    = bool
  default = true
}

variable "kms_key_arn" {
  type = string
}
