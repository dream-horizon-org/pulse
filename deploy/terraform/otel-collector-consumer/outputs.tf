output "otel_consumer_route53_fqdns" {
  description = "Route53 alias FQDN for OTEL consumer ALB"
  value       = [aws_route53_record.otel_consumer.fqdn]
}

output "otel_consumer_alb_dns_name" {
  description = "DNS name of OTEL consumer ALB"
  value       = aws_lb.otel-consumer.dns_name
}
