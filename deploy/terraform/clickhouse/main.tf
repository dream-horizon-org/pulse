terraform {
  required_version = ">= 1.3.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

locals {
  # Subnet chosen based on AZ name
  selected_subnet_id = var.subnets[var.availability_zone]
}

# -------------------------------
# EC2 instances for ClickHouse
# -------------------------------
resource "aws_instance" "clickhouse" {
  count                  = var.shard_count
  ami                    = var.ami_id
  instance_type          = var.instance_type

  subnet_id              = local.selected_subnet_id
  vpc_security_group_ids = var.vpc_security_group_ids
  key_name               = var.key_name
  iam_instance_profile   = var.iam_instance_profile
  associate_public_ip_address = false

  instance_market_options {
    market_type = "spot"
  }

  # Tag each instance with cluster + shard index
  tags = {
    Name             = "${var.cluster_name}-shard-${count.index + 1}"
    ClusterName      = var.cluster_name
    ShardIndex       = count.index + 1
    AvailabilityZone = var.availability_zone
    service          = "pulse"
  }

  # Bootstrap script: mount secondary volume & configure ClickHouse
  user_data = templatefile("${path.module}/user_data.sh.tpl", {
    cluster_name    = var.cluster_name
    shard_index     = count.index + 1
    shard_count     = var.shard_count
    data_device     = "/dev/xvdb"             # device name we'll attach below
    data_mount_path = "/var/lib/clickhouse"   # adjust to your AMI's ClickHouse data path
    private_zone    = var.private_zone_domain # used to build hostnames
  })

  # Root volume (OS)
  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20
    delete_on_termination = true
  }

  # Secondary data volume (will be /dev/xvdb inside the instance)
  ebs_block_device {
    device_name           = "/dev/xvdb"
    volume_type           = var.data_volume_type
    volume_size           = var.data_volume_size
    iops                  = 3000
    throughput            = 125
    encrypted             = false
    delete_on_termination = true
  }

  volume_tags = {
    Name        = "${var.cluster_name}-shard-${count.index + 1}-volume"
    ClusterName = var.cluster_name
    ShardIndex  = count.index + 1
    Role        = "clickhouse"
    service     = "pulse"
  }
}

# -------------------------------
# Route53 records for each shard
# These hostnames will be used in cluster config
# -------------------------------
resource "aws_route53_record" "clickhouse_shard" {
  count   = var.shard_count
  zone_id = var.private_zone_id

  # e.g. shard-1.my-clickhouse-cluster.internal.example.com
  name = "shard-${count.index + 1}.${var.cluster_name}.${var.private_zone_domain}"
  type = "A"
  ttl  = 10

  records = [aws_instance.clickhouse[count.index].private_ip]
}

# -------------------------------
# Outputs
# -------------------------------
output "clickhouse_private_ips" {
  value = [for i in aws_instance.clickhouse : i.private_ip]
  description = "clickhouse private IPs"
}

output "clickhouse_hostnames" {
  value = [for i in range(var.shard_count) :
    "shard-${i + 1}.${var.cluster_name}.${var.private_zone_domain}"
  ]
  description = "hostnames of the clickhouse shards"
}
