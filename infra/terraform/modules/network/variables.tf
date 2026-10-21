variable "name" {
  type = string
}

variable "region" {
  type = string
}

variable "cidr" {
  type    = string
  default = "10.40.0.0/16"
}

variable "az_count" {
  type    = number
  default = 3
  validation {
    condition     = var.az_count >= 2 && var.az_count <= 3
    error_message = "Use 2 or 3 availability zones."
  }
}

variable "single_nat_gateway" {
  description = "One NAT for all AZs (cheaper, dev only). Production uses one per AZ."
  type        = bool
  default     = false
}

variable "kms_key_arn" {
  type = string
}

variable "log_retention_days" {
  type    = number
  default = 365
}
