output "otel_consumer_route53_fqdns" {
  description = "Private DNS names for each OTEL consumer instance (pulse-otel-consumer-NN.<zone>)"
  value       = [for r in aws_route53_record.otel_consumer_node : r.fqdn]
}

output "otel_consumer_instance_by_hostname_suffix" {
  description = "Map of NN (01,02,...) to EC2 instance id — numbering from sort(instance_id)"
  value       = local.otel_consumer_indexed
}
