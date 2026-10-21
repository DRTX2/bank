provider "aws" {
  region = var.region
  default_tags {
    tags = {
      Project     = "kuntur"
      Environment = var.environment
      ManagedBy   = "terraform"
      Repository  = var.github_repository
    }
  }
}

module "platform" {
  source = "../../modules/platform"

  environment                 = var.environment
  vpc_cidr                    = var.vpc_cidr
  az_count                    = var.az_count
  single_nat_gateway          = var.single_nat_gateway
  deletion_protection         = var.deletion_protection
  db_instance_class           = var.db_instance_class
  db_multi_az                 = var.db_multi_az
  min_tasks                   = var.min_tasks
  max_tasks                   = var.max_tasks
  domain_name                 = var.domain_name
  api_origin_domain           = var.api_origin_domain
  alb_certificate_arn         = var.alb_certificate_arn
  cloudfront_certificate_arn  = var.cloudfront_certificate_arn
  github_repository           = var.github_repository
  create_github_oidc_provider = var.create_github_oidc_provider
  alarm_email                 = var.alarm_email
}

output "platform" {
  value = module.platform
}
