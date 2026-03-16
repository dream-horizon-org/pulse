import {
  Container,
  Title,
  Text,
  Stack,
  Card,
  Group,
  Button,
  Badge,
  List,
  ThemeIcon,
  Box,
  Loader,
  Modal,
  Textarea,
} from "@mantine/core";
import {
  IconCheck,
  IconMail,
  IconRocket,
  IconBuilding,
  IconCircleCheck,
  IconUsers,
} from "@tabler/icons-react";
import { notifications } from "@mantine/notifications";
import { useTenantContext } from "../../contexts";
import { useNavigate } from "react-router-dom";
import { getCookies } from "../../helpers/cookies";
import { COOKIES_KEY } from "../../constants";
import { useContactUs, useContactSupport } from "../../hooks";
import classes from "./Pricing.module.css";
import { TIERS } from "../../constants/Tiers";
import { TENANT_ROLES } from "../../constants/Roles";
import { useState } from "react";

export function Pricing() {
  const { tier, userRole } = useTenantContext();
  const navigate = useNavigate();
  const currentPlan = tier || TIERS.FREE;
  const isTenantAdmin = userRole === TENANT_ROLES.ADMIN;
  const contactUsMutation = useContactUs();
  const contactSupportMutation = useContactSupport();

  const [modalOpen, setModalOpen] = useState(false);
  const [modalType, setModalType] = useState<"sales" | "support">("sales");
  const [message, setMessage] = useState("");

  const MAX_MESSAGE_LENGTH = 4000;

  const openContactModal = (type: "sales" | "support") => {
    const accessToken = getCookies(COOKIES_KEY.ACCESS_TOKEN);
    if (!accessToken || accessToken === "undefined") {
      notifications.show({
        title: "Sign in required",
        message:
          type === "sales"
            ? "Please sign in to submit a contact request, or email us at sales@pulse.io"
            : "Please sign in to submit a support request, or email us at support@pulse.io",
        color: "yellow",
      });
      window.open(
        type === "sales"
          ? "mailto:sales@pulse.io?subject=Enterprise Plan Inquiry"
          : "mailto:support@pulse.io?subject=Enterprise Support Request",
        "_blank",
      );
      return;
    }

    setModalType(type);
    setMessage("");
    setModalOpen(true);
  };

  const handleSubmitContact = () => {
    const mutation =
      modalType === "sales" ? contactUsMutation : contactSupportMutation;
    const messageToSend = message.trim() || null;

    mutation.mutate(
      { message: messageToSend },
      {
        onSuccess: () => {
          setModalOpen(false);
          setMessage("");
          notifications.show({
            title: "Success",
            message:
              modalType === "sales"
                ? "Contact request submitted successfully. Our team will reach out soon."
                : "Support request submitted successfully.",
            color: "green",
          });
        },
        onError: (error) => {
          notifications.show({
            title: "Error",
            message:
              error instanceof Error
                ? error.message
                : `Failed to submit ${modalType === "sales" ? "contact" : "support"} request`,
            color: "red",
          });
        },
      },
    );
  };

  // If user is already on Enterprise, show different UI
  if (tier === TIERS.ENTERPRISE) {
    return (
      <>
        <Modal
          opened={modalOpen}
          onClose={() => setModalOpen(false)}
          title={
            <Title order={3}>
              {modalType === "sales" ? "Contact Sales" : "Contact Support"}
            </Title>
          }
          size="lg"
        >
          <Stack gap="md">
            <Text size="sm" c="dimmed">
              {modalType === "sales"
                ? "Tell us about your needs and our sales team will get back to you."
                : "Describe your issue and our support team will assist you."}
            </Text>
            <Textarea
              placeholder="Enter your message (optional)"
              value={message}
              onChange={(e) => setMessage(e.currentTarget.value)}
              minRows={6}
              maxRows={12}
              maxLength={MAX_MESSAGE_LENGTH}
              description={`${message.length} / ${MAX_MESSAGE_LENGTH} characters`}
              autoFocus
            />
            <Group justify="flex-end" gap="sm">
              <Button
                variant="subtle"
                onClick={() => setModalOpen(false)}
                disabled={
                  contactUsMutation.isPending ||
                  contactSupportMutation.isPending
                }
              >
                Cancel
              </Button>
              <Button
                onClick={handleSubmitContact}
                loading={
                  contactUsMutation.isPending ||
                  contactSupportMutation.isPending
                }
                style={{
                  background:
                    "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                }}
              >
                Submit Request
              </Button>
            </Group>
          </Stack>
        </Modal>

        <Box className={classes.container}>
          <Container size="lg" py="xl">
            <Stack
              gap="xl"
              align="center"
              style={{ maxWidth: 800, margin: "0 auto", paddingTop: "3rem" }}
            >
              {/* Success Icon */}
              <ThemeIcon
                size={100}
                radius="xl"
                style={{
                  background: "#0ec9c2",
                  color: "white",
                }}
              >
                <IconCircleCheck size={60} />
              </ThemeIcon>

              {/* Heading */}
              <Stack gap="sm" align="center">
                <Title
                  order={1}
                  style={{ fontSize: "2.5rem", textAlign: "center" }}
                >
                  You're on Enterprise Plan
                </Title>
                <Text size="lg" c="dimmed" ta="center" maw={600}>
                  Unlock the full power of Pulse with unlimited projects and
                  advanced features
                </Text>
              </Stack>

              {/* Features Card */}
              <Card
                shadow="md"
                radius="lg"
                padding="xl"
                withBorder
                style={{ width: "100%" }}
              >
                <Stack gap="md">
                  <Group gap="xs" mb="md">
                    <ThemeIcon
                      size="lg"
                      radius="md"
                      style={{
                        background: "#0ec9c2",
                        color: "white",
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
                          background: "#0ec9c2",
                          color: "white",
                        }}
                      >
                        <IconCheck size={14} />
                      </ThemeIcon>
                    }
                  >
                    <List.Item>
                      <strong>Unlimited projects</strong>
                    </List.Item>
                    <List.Item>
                      <strong>Unlimited team members</strong>
                    </List.Item>
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
                  onClick={() => navigate("/organization/members")}
                  style={{
                    background:
                      "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                    border: "none",
                    fontWeight: 600,
                  }}
                >
                  Manage Team
                </Button>
                {isTenantAdmin && (
                  <Button
                    size="lg"
                    radius="xl"
                    variant="outline"
                    leftSection={
                      contactSupportMutation.isPending ? (
                        <Loader size="sm" color="#0ec9c2" />
                      ) : (
                        <IconMail size={20} />
                      )
                    }
                    onClick={() => openContactModal("support")}
                    loading={contactSupportMutation.isPending}
                    disabled={contactSupportMutation.isPending}
                    style={{
                      borderColor: "#0ec9c2",
                      color: "#0ec9c2",
                      fontWeight: 600,
                    }}
                  >
                    Contact Support
                  </Button>
                )}
              </Group>

              {/* Footer Note */}
              {isTenantAdmin && (
                <Text size="sm" c="dimmed" ta="center" mt="xl">
                  Need to discuss your plan?{" "}
                  <Text
                    component="span"
                    style={{ color: "#0ec9c2", cursor: "pointer" }}
                    fw={600}
                    onClick={() => openContactModal("support")}
                  >
                    Contact our support team
                  </Text>{" "}
                  for assistance.
                </Text>
              )}
            </Stack>
          </Container>
        </Box>
      </>
    );
  }

  // Free tier user - show pricing comparison
  return (
    <>
      <Modal
        opened={modalOpen}
        onClose={() => setModalOpen(false)}
        title={
          <Title order={3}>
            {modalType === "sales" ? "Contact Sales" : "Contact Support"}
          </Title>
        }
        size="lg"
      >
        <Stack gap="md">
          <Text size="sm" c="dimmed">
            {modalType === "sales"
              ? "Tell us about your needs and our sales team will get back to you."
              : "Describe your issue and our support team will assist you."}
          </Text>
          <Textarea
            placeholder="Enter your message (optional)"
            value={message}
            onChange={(e) => setMessage(e.currentTarget.value)}
            minRows={6}
            maxRows={12}
            maxLength={MAX_MESSAGE_LENGTH}
            description={`${message.length} / ${MAX_MESSAGE_LENGTH} characters`}
            autoFocus
          />
          <Group justify="flex-end" gap="sm">
            <Button
              variant="subtle"
              onClick={() => setModalOpen(false)}
              disabled={
                contactUsMutation.isPending || contactSupportMutation.isPending
              }
            >
              Cancel
            </Button>
            <Button
              onClick={handleSubmitContact}
              loading={
                contactUsMutation.isPending || contactSupportMutation.isPending
              }
              style={{
                background: "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
              }}
            >
              Submit Request
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Box className={classes.container}>
        <Container size="lg" py="xl">
          <Stack gap="xl" align="center">
            {/* Header */}
            <Stack gap="md" align="center" className={classes.header}>
              <Title order={1} className={classes.title}>
                Choose Your Plan
              </Title>
              <Text size="lg" c="dimmed" ta="center" maw={600}>
                Start with our free plan and upgrade as you grow. No credit card
                required.
              </Text>
            </Stack>

            {/* Pricing Cards */}
            <div className={classes.pricingGrid}>
              {/* Free Plan */}
              <Card
                shadow="md"
                padding="xl"
                radius="lg"
                className={`${classes.pricingCard} ${currentPlan === TIERS.FREE ? classes.currentPlan : ""}`}
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
                            background: "rgba(14, 201, 194, 0.1)",
                            color: "#0ec9c2",
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
                            background: "#0ec9c2",
                            color: "white",
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
                          background: "#0ec9c2",
                          color: "white",
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
                    style={
                      currentPlan === TIERS.FREE
                        ? {
                            background: "#0ec9c2",
                            color: "white",
                            fontWeight: 600,
                            cursor: "not-allowed",
                            opacity: 0.6,
                          }
                        : {
                            background: "transparent",
                            border: "2px solid #0ec9c2",
                            color: "#0ec9c2",
                            fontWeight: 600,
                          }
                    }
                  >
                    {currentPlan === TIERS.FREE
                      ? "Current Plan"
                      : "Get Started"}
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
                            background: "#0ec9c2",
                            color: "white",
                          }}
                        >
                          <IconBuilding size={20} />
                        </ThemeIcon>
                        <Title order={2}>Enterprise</Title>
                      </Group>
                      <Badge
                        size="lg"
                        style={{
                          background: "#0ec9c2",
                          color: "white",
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
                          background: "#0ec9c2",
                          color: "white",
                        }}
                      >
                        <IconCheck size={12} />
                      </ThemeIcon>
                    }
                  >
                    <List.Item>
                      <strong>Unlimited projects</strong>
                    </List.Item>
                    <List.Item>
                      <strong>Unlimited team members</strong>
                    </List.Item>
                    <List.Item>Advanced analytics & monitoring</List.Item>
                    <List.Item>Custom data retention</List.Item>
                    <List.Item>Priority support & SLA</List.Item>
                    <List.Item>Custom integrations</List.Item>
                    <List.Item>On-premise deployment option</List.Item>
                    <List.Item>Dedicated account manager</List.Item>
                  </List>

                  {/* CTA Button */}
                  {isTenantAdmin && (
                    <Button
                      fullWidth
                      size="lg"
                      radius="xl"
                      leftSection={
                        contactUsMutation.isPending ? (
                          <Loader size="sm" color="white" />
                        ) : (
                          <IconMail size={18} />
                        )
                      }
                      onClick={() => openContactModal("sales")}
                      loading={contactUsMutation.isPending}
                      disabled={contactUsMutation.isPending}
                      style={{
                        background:
                          "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                        border: "none",
                        fontWeight: 600,
                        color: "white",
                      }}
                    >
                      Contact Sales
                    </Button>
                  )}
                </Stack>
              </Card>
            </div>

            {/* Footer Note */}
            {isTenantAdmin && (
              <Text size="sm" c="dimmed" ta="center" mt="xl">
                Need help choosing?{" "}
                <Text
                  component="span"
                  style={{ color: "#0ec9c2", cursor: "pointer" }}
                  fw={600}
                  onClick={() => openContactModal("sales")}
                >
                  Contact our team
                </Text>{" "}
                for personalized recommendations.
              </Text>
            )}
          </Stack>
        </Container>
      </Box>
    </>
  );
}
