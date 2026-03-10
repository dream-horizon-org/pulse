import { Container, Title, Text, Stack, Card, Group, Button, Badge, List, ThemeIcon, Box } from '@mantine/core';
import { IconCheck, IconMail, IconRocket, IconBuilding, IconCircleCheck, IconUsers } from '@tabler/icons-react';
import { useTenantContext } from '../../contexts';
import { useNavigate } from 'react-router-dom';
import classes from './Pricing.module.css';
import { TIERS } from '../../constants/Tiers';

export function Pricing() {
  const { tier } = useTenantContext();
  const navigate = useNavigate();
  const currentPlan = tier || TIERS.FREE;

  const handleContactUs = () => {
    window.open('mailto:sales@yourcompany.com?subject=Enterprise Plan Inquiry', '_blank');
  };

  const handleContactSupport = () => {
    window.open('mailto:support@yourcompany.com?subject=Enterprise Support Request', '_blank');
  };

  // If user is already on Enterprise, show different UI
  if (tier === TIERS.ENTERPRISE) {
    return (
      <Box className={classes.container}>
        <Container size="lg" py="xl">
          <Stack gap="xl" align="center" style={{ maxWidth: 800, margin: '0 auto', paddingTop: '3rem' }}>
            {/* Success Icon */}
            <ThemeIcon 
              size={100} 
              radius="xl" 
              style={{
                background: '#0ec9c2',
                color: 'white',
              }}
            >
              <IconCircleCheck size={60} />
            </ThemeIcon>

            {/* Heading */}
            <Stack gap="sm" align="center">
              <Title order={1} style={{ fontSize: '2.5rem', textAlign: 'center' }}>
                You're on Enterprise Plan
              </Title>
              <Text size="lg" c="dimmed" ta="center" maw={600}>
                Unlock the full power of Pulse with unlimited projects and advanced features
              </Text>
            </Stack>

            {/* Features Card */}
            <Card shadow="md" radius="lg" padding="xl" withBorder style={{ width: '100%' }}>
              <Stack gap="md">
                <Group gap="xs" mb="md">
                  <ThemeIcon 
                    size="lg" 
                    radius="md" 
                    style={{
                      background: '#0ec9c2',
                      color: 'white',
                    }}
                  >
                    <IconBuilding size={24} />
                  </ThemeIcon>
                  <Title order={3}>Your Enterprise Benefits</Title>
                </Group>
                <List
                  spacing="sm"
                  size="md"
                  icon={
                    <ThemeIcon 
                      size={22} 
                      radius="xl"
                      style={{
                        background: '#0ec9c2',
                        color: 'white',
                      }}
                    >
                      <IconCheck size={14} />
                    </ThemeIcon>
                  }
                >
                  <List.Item><strong>Unlimited projects</strong></List.Item>
                  <List.Item><strong>Unlimited team members</strong></List.Item>
                  <List.Item>Advanced analytics & monitoring</List.Item>
                  <List.Item>Custom data retention</List.Item>
                  <List.Item>Priority support & SLA</List.Item>
                  <List.Item>Custom integrations</List.Item>
                  <List.Item>On-premise deployment option</List.Item>
                  <List.Item>Dedicated account manager</List.Item>
                </List>
              </Stack>
            </Card>

            {/* Action Buttons */}
            <Group gap="md" mt="lg">
              <Button 
                size="lg" 
                radius="xl"
                leftSection={<IconUsers size={20} />}
                onClick={() => navigate('/organization/members')}
                style={{
                  background: "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                  border: "none",
                  fontWeight: 600,
                }}
              >
                Manage Team
              </Button>
              <Button 
                size="lg" 
                radius="xl"
                variant="outline"
                leftSection={<IconMail size={20} />}
                onClick={handleContactSupport}
                style={{
                  borderColor: '#0ec9c2',
                  color: '#0ec9c2',
                  fontWeight: 600,
                }}
              >
                Contact Support
              </Button>
            </Group>

            {/* Footer Note */}
            <Text size="sm" c="dimmed" ta="center" mt="xl">
              Need to discuss your plan? <Text component="span" style={{ color: '#0ec9c2' }} fw={600} onClick={handleContactSupport}>Contact our support team</Text> for assistance.
            </Text>
          </Stack>
        </Container>
      </Box>
    );
  }

  // Free tier user - show pricing comparison
  return (
    <Box className={classes.container}>
      <Container size="lg" py="xl">
        <Stack gap="xl" align="center">
          {/* Header */}
          <Stack gap="md" align="center" className={classes.header}>
            <Title order={1} className={classes.title}>
              Choose Your Plan
            </Title>
            <Text size="lg" c="dimmed" ta="center" maw={600}>
              Start with our free plan and upgrade as you grow. No credit card required.
            </Text>
          </Stack>

          {/* Pricing Cards */}
          <div className={classes.pricingGrid}>
            {/* Free Plan */}
            <Card
              shadow="md"
              padding="xl"
              radius="lg"
              className={`${classes.pricingCard} ${currentPlan === TIERS.FREE ? classes.currentPlan : ''}`}
              withBorder
            >
              <Stack gap="lg">
                {/* Plan Header */}
                <div>
                  <Group justify="space-between" mb="xs">
                    <Group gap="xs">
                      <ThemeIcon 
                        size="lg" 
                        radius="md" 
                        variant="light" 
                        style={{
                          background: 'rgba(14, 201, 194, 0.1)',
                          color: '#0ec9c2',
                        }}
                      >
                        <IconRocket size={20} />
                      </ThemeIcon>
                      <Title order={2}>Free</Title>
                    </Group>
                    {currentPlan === TIERS.FREE && (
                      <Badge 
                        size="lg"
                        style={{
                          background: '#0ec9c2',
                          color: 'white',
                        }}
                      >
                        Current Plan
                      </Badge>
                    )}
                  </Group>
                  <Text c="dimmed" size="sm">
                    Perfect for getting started
                  </Text>
                </div>

                {/* Price */}
                <div>
                  <Group align="baseline" gap="xs">
                    <Text size="48px" fw={700} lh={1}>
                      $0
                    </Text>
                    <Text size="lg" c="dimmed">
                      / month
                    </Text>
                  </Group>
                </div>

                {/* Features */}
                <List
                  spacing="sm"
                  size="sm"
                  icon={
                    <ThemeIcon 
                      size={20} 
                      radius="xl"
                      style={{
                        background: '#0ec9c2',
                        color: 'white',
                      }}
                    >
                      <IconCheck size={12} />
                    </ThemeIcon>
                  }
                >
                  <List.Item>1 Project</List.Item>
                  <List.Item>Up to 5 team members</List.Item>
                  <List.Item>Basic analytics & monitoring</List.Item>
                  <List.Item>7 days data retention</List.Item>
                  <List.Item>Community support</List.Item>
                  <List.Item>SDK for Android, iOS, React Native</List.Item>
                </List>

                {/* CTA Button */}
                <Button
                  fullWidth
                  size="lg"
                  radius="xl"
                  disabled={currentPlan === TIERS.FREE}
                  style={currentPlan === TIERS.FREE ? {
                    background: '#0ec9c2',
                    color: 'white',
                    fontWeight: 600,
                    cursor: 'not-allowed',
                    opacity: 0.6,
                  } : {
                    background: 'transparent',
                    border: '2px solid #0ec9c2',
                    color: '#0ec9c2',
                    fontWeight: 600,
                  }}
                >
                  {currentPlan === TIERS.FREE ? 'Current Plan' : 'Get Started'}
                </Button>
              </Stack>
            </Card>

            {/* Enterprise Plan */}
            <Card
              shadow="xl"
              padding="xl"
              radius="lg"
              className={`${classes.pricingCard} ${classes.enterpriseCard}`}
              withBorder
            >
              <Stack gap="lg">
                {/* Plan Header */}
                <div>
                  <Group justify="space-between" mb="xs">
                    <Group gap="xs">
                      <ThemeIcon 
                        size="lg" 
                        radius="md"
                        style={{
                          background: '#0ec9c2',
                          color: 'white',
                        }}
                      >
                        <IconBuilding size={20} />
                      </ThemeIcon>
                      <Title order={2}>Enterprise</Title>
                    </Group>
                    <Badge 
                      size="lg"
                      style={{
                        background: '#0ec9c2',
                        color: 'white',
                      }}
                    >
                      Popular
                    </Badge>
                  </Group>
                  <Text c="dimmed" size="sm">
                    For teams that need more
                  </Text>
                </div>

                {/* Price */}
                <div>
                  <Group align="baseline" gap="xs">
                    <Text size="48px" fw={700} lh={1}>
                      Custom
                    </Text>
                  </Group>
                  <Text size="sm" c="dimmed" mt="xs">
                    Tailored to your needs
                  </Text>
                </div>

                {/* Features */}
                <List
                  spacing="sm"
                  size="sm"
                  icon={
                    <ThemeIcon 
                      size={20} 
                      radius="xl"
                      style={{
                        background: '#0ec9c2',
                        color: 'white',
                      }}
                    >
                      <IconCheck size={12} />
                    </ThemeIcon>
                  }
                >
                  <List.Item><strong>Unlimited projects</strong></List.Item>
                  <List.Item><strong>Unlimited team members</strong></List.Item>
                  <List.Item>Advanced analytics & monitoring</List.Item>
                  <List.Item>Custom data retention</List.Item>
                  <List.Item>Priority support & SLA</List.Item>
                  <List.Item>Custom integrations</List.Item>
                  <List.Item>On-premise deployment option</List.Item>
                  <List.Item>Dedicated account manager</List.Item>
                </List>

                {/* CTA Button */}
                <Button
                  fullWidth
                  size="lg"
                  radius="xl"
                  leftSection={<IconMail size={18} />}
                  onClick={handleContactUs}
                  style={{
                    background: "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                    border: "none",
                    fontWeight: 600,
                    color: 'white',
                  }}
                >
                  Contact Sales
                </Button>
              </Stack>
            </Card>
          </div>

          {/* Footer Note */}
          <Text size="sm" c="dimmed" ta="center" mt="xl">
            Need help choosing? <Text component="span" style={{ color: '#0ec9c2', cursor: 'pointer' }} fw={600}>Contact our team</Text> for personalized recommendations.
          </Text>
        </Stack>
      </Container>
    </Box>
  );
}
