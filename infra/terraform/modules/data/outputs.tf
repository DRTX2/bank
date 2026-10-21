output "db_endpoint" {
  value = aws_db_instance.this.address
}

output "db_master_secret_arn" {
  value = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "db_instance_id" {
  value = aws_db_instance.this.identifier
}

output "kafka_cluster_arn" {
  value = aws_msk_serverless_cluster.this.arn
}

output "kafka_cluster_name" {
  value = aws_msk_serverless_cluster.this.cluster_name
}

output "kafka_bootstrap_brokers" {
  value = aws_msk_serverless_cluster.this.bootstrap_brokers_sasl_iam
}
