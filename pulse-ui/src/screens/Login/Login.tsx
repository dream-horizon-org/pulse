import {
  Box,
  Text,
  useMantineTheme,
  Stack,
  Container,
  Image,
  Button,
} from "@mantine/core";
import { signInWithPopup, GoogleAuthProvider } from "firebase/auth";
import { getFirebaseAuth } from "../../config/firebase";
import classes from "./Login.module.css";
import {
  COMMON_CONSTANTS,
  COOKIES_KEY,
  LOGIN_PAGE_CONSTANTS,
  ROUTES,
} from "../../constants";
import { useNavigate } from "react-router-dom";
import { IconSquareRoundedX } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { LoaderWithMessage } from "../../components/LoaderWithMessage";
import { setCookiesAfterAuthentication } from "../../helpers/setCookiesAfterAuthentication";
import { showNotification } from "../../helpers/showNotification";
import { getCookies } from "../../helpers/cookies";
import { checkRefreshTokenExpiration } from "../../helpers/checkRefreshTokenExpiration";
import { logEvent } from "../../helpers/googleAnalytics";
import { login } from "../../helpers/login";
import { useTenantContext } from "../../contexts";

export function Login() {
  const navigate = useNavigate();
  const theme = useMantineTheme();
  const { setTenantInfo } = useTenantContext();
  const [isFetchingTokensFromServer, setIsFetchingTokensFromServer] =
    useState<boolean>(false);

  const googleOAuthEnabled = process.env.REACT_APP_GOOGLE_OAUTH_ENABLED;
  const googleClientId = process.env.REACT_APP_GOOGLE_CLIENT_ID ?? "";
  const isDevMode = process.env.NODE_ENV === "development";

  // Determine if dummy login should be shown
  const shouldShowDummyLogin =
    googleOAuthEnabled === "false" ||
    (isDevMode && (!googleClientId || googleClientId.trim() === ""));

  useEffect(() => {
    const refreshToken = getCookies(COOKIES_KEY.REFRESH_TOKEN);
    if (refreshToken && refreshToken !== "undefined") {
      const isRefreshTokenExpired = checkRefreshTokenExpiration(refreshToken);
      if (!isRefreshTokenExpired) {
        navigate(ROUTES.PROJECT_SELECTION.basePath);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const showErrorToast = (errorMessage: string) => {
    showNotification(
      COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
      errorMessage,
      <IconSquareRoundedX />,
      theme.colors.red[6],
    );
  };

  const onFirebaseGoogleLogin = async () => {
    console.log("[Login] Starting Firebase Google login...");
    setIsFetchingTokensFromServer(true);
    logEvent("User logged in", ROUTES.LOGIN.key);
    
    try {
      console.log("[Login] Step 1: Getting Firebase Auth instance...");
      const auth = getFirebaseAuth();
      console.log("[Login] ✅ Firebase Auth instance obtained");
      
      console.log("[Login] Step 2: Creating Google Auth Provider...");
      const provider = new GoogleAuthProvider();
      console.log("[Login] ✅ Google Auth Provider created");
      
      console.log("[Login] Step 3: Initiating sign-in popup...");
      const result = await signInWithPopup(auth, provider);
      console.log("[Login] ✅ Sign-in popup completed successfully");
      console.log("[Login] User email:", result.user.email);
      
      console.log("[Login] Step 4: Getting Firebase ID token...");
      const firebaseToken = await result.user.getIdToken();
      console.log("[Login] ✅ Firebase ID token obtained (length:", firebaseToken.length, ")");
      
      console.log("[Login] Step 5: Calling backend /v1/auth/login...");
      const { data, error } = await login(firebaseToken);
      
      if (data) {
        console.log("[Login] ✅ Backend login successful");
        console.log("[Login] User data:", {
          userId: data.userId,
          email: data.email,
          needsOnboarding: data.needsOnboarding,
        });
        
        if (data.needsOnboarding) {
          console.log("[Login] User needs onboarding, storing temp data and redirecting...");
          sessionStorage.setItem('onboarding_user', JSON.stringify({
            userId: data.userId,
            email: data.email,
            name: data.name,
          }));
          // Store the Firebase token for onboarding API call
          sessionStorage.setItem('firebase_token', firebaseToken);
          navigate(ROUTES.ONBOARDING.basePath);
        } else {
          console.log("[Login] User authenticated, setting tenant context and redirecting...");
          // Set auth tokens in cookies
          await setCookiesAfterAuthentication(data);
          
          // Set tenant context
          if (data.tenantId && data.tenantRole) {
            setTenantInfo({
              tenantId: data.tenantId,
              tenantName: '', // Will be fetched from projects API
              userRole: data.tenantRole as 'admin' | 'member',
              tier: data.tier || 'free',
            });
          }
          
          navigate(ROUTES.PROJECT_SELECTION.basePath);
        }
      }
      
      if (error) {
        console.error("[Login] ❌ Backend login error:", error);
        showErrorToast(error.message);
      }
    } catch (error: any) {
      console.error("[Login] ❌ Firebase login error:", error);
      console.error("[Login] Error code:", error.code);
      console.error("[Login] Error message:", error.message);
      console.error("[Login] Full error:", error);
      showErrorToast(error.message || COMMON_CONSTANTS.DEFAULT_ERROR_MESSAGE);
    }
    
    setIsFetchingTokensFromServer(false);
    console.log("[Login] Login flow completed");
  };

  const onDummyLogin = async () => {
    setIsFetchingTokensFromServer(true);
    logEvent("User logged in (dummy)", ROUTES.LOGIN.key);
    
    const { data, error } = await login("dev-id-token");
    
    if (data) {
      if (data.needsOnboarding) {
        sessionStorage.setItem('onboarding_user', JSON.stringify({
          userId: data.userId,
          email: data.email,
          name: data.name,
        }));
        // Store the dev token for onboarding API call
        sessionStorage.setItem('firebase_token', 'dev-id-token');
        navigate(ROUTES.ONBOARDING.basePath);
      } else {
        // Set auth tokens in cookies
        await setCookiesAfterAuthentication(data);
        
        // Set tenant context
        if (data.tenantId && data.tenantRole) {
          setTenantInfo({
            tenantId: data.tenantId,
            tenantName: '', // Will be fetched from projects API
            userRole: data.tenantRole as 'admin' | 'member',
            tier: data.tier || 'free',
          });
        }
        
        navigate(ROUTES.PROJECT_SELECTION.basePath);
      }
    }
    
    if (error) {
      showErrorToast(error.message);
    }
    
    setIsFetchingTokensFromServer(false);
  };

  return (
    <Box className={classes.loginContainer}>
      <Container size="fluid" className={classes.mainWrapper}>
        <div className={classes.splitLayout}>
          {/* Left Side - Dashboard Preview */}
          <Box className={classes.leftSide}>
            <Box className={classes.dashboardPreview}>
              <Image
                src={(process.env.PUBLIC_URL || '') + "/landing_page.png"}
                alt="Dashboard Preview"
                className={classes.previewImage}
              />
            </Box>
          </Box>

          {/* Right Side - Hero & Login */}
          <Box className={classes.rightSide}>
            <Stack
              align="center"
              justify="center"
              className={classes.rightContent}
            >
              <Stack align="center" gap="md" className={classes.heroSection}>
                <h1 className={classes.mainHeading}>
                  All Your App Insights in One Dashboard
                </h1>
                <Text className={classes.subtitle}>
                  Real-time visibility into what matters most - your users'
                  experience
                </Text>
              </Stack>

              <Box className={classes.loginCard}>
                {isFetchingTokensFromServer ? (
                  <LoaderWithMessage
                    className={classes.loader}
                    loadingMessage={LOGIN_PAGE_CONSTANTS.SIGNING_IN_MESSAGE}
                  />
                ) : (
                  <Stack align="center" gap="lg">
                    <Text className={classes.loginPrompt}>
                      Sign in to get started
                    </Text>
                    {shouldShowDummyLogin ? (
                      <Button
                        size="lg"
                        radius="xl"
                        onClick={onDummyLogin}
                        style={{
                          background:
                            "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                          border: "none",
                          fontWeight: 600,
                          fontSize: "16px",
                          padding: "12px 32px",
                          minWidth: "240px",
                        }}
                      >
                        Sign in (Dev Mode)
                      </Button>
                    ) : (
                      <Button
                        size="lg"
                        radius="xl"
                        onClick={onFirebaseGoogleLogin}
                        style={{
                          background:
                            "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                          border: "none",
                          fontWeight: 600,
                          fontSize: "16px",
                          padding: "12px 32px",
                          minWidth: "240px",
                        }}
                      >
                        Sign in with Google
                      </Button>
                    )}
                    <Text className={classes.loginSubtext}>
                      {shouldShowDummyLogin
                        ? "Development mode: Using dummy authentication"
                        : "Access powerful analytics and insights"}
                    </Text>
                  </Stack>
                )}
              </Box>
            </Stack>
          </Box>
        </div>
      </Container>

      {/* Background decorative elements */}
      <div className={classes.bgDecoration1}></div>
      <div className={classes.bgDecoration2}></div>
      <div className={classes.bgDecoration3}></div>
    </Box>
  );
}
