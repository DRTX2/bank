# Cost-conscious dev: 2 AZs, one NAT, single-AZ database, no deletion protection.
region                      = "us-east-1"
environment                 = "dev"
vpc_cidr                    = "10.40.0.0/16"
az_count                    = 2
single_nat_gateway          = true
deletion_protection         = false
db_instance_class           = "db.t4g.small"
db_multi_az                 = false
min_tasks                   = 1
max_tasks                   = 2
domain_name                 = "dev.kuntur.example"
api_origin_domain           = "origin-dev.kuntur.example"
alb_certificate_arn         = "arn:aws:acm:us-east-1:111111111111:certificate/replace-me"
cloudfront_certificate_arn  = "arn:aws:acm:us-east-1:111111111111:certificate/replace-me"
github_repository           = "DRTX2/bank"
create_github_oidc_provider = true
