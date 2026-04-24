output "nlb_dns_name" {
  description = "DNS name of the internal NLB (use for routing from Kong or internal services)"
  value       = aws_lb.capture.dns_name
}

output "nlb_arn" {
  description = "ARN of the NLB"
  value       = aws_lb.capture.arn
}

output "target_group_arn" {
  description = "ARN of the target group (for health check monitoring)"
  value       = aws_lb_target_group.capture.arn
}

output "asg_name" {
  description = "Name of the Auto Scaling Group"
  value       = aws_autoscaling_group.capture.name
}

output "asg_desired_capacity" {
  description = "Current desired capacity of the ASG"
  value       = aws_autoscaling_group.capture.desired_capacity
}

output "launch_template_id" {
  description = "ID of the launch template (for troubleshooting)"
  value       = aws_launch_template.capture.id
}

output "launch_template_latest_version" {
  description = "Latest version of the launch template"
  value       = aws_launch_template.capture.latest_version
}

output "route53_fqdn" {
  description = "Fully qualified DNS name for the capture service"
  value       = aws_route53_record.capture.fqdn
}

output "service_url" {
  description = "Complete URL for the session capture service (internal)"
  value       = "http://${aws_route53_record.capture.fqdn}:${var.listen_port}"
}

output "health_check_url" {
  description = "Health check endpoint URL"
  value       = "http://${aws_route53_record.capture.fqdn}:${var.listen_port}${var.health_check_path}"
}
