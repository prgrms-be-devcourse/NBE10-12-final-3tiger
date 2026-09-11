import { Ionicons } from "@expo/vector-icons";
import { Tabs } from "expo-router";
import { Platform } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useThemeStore } from "@/stores/theme-store";

const GREEN = "#067A3D";

export default function TabLayout() {
  const isDark = useThemeStore((state) => state.isDark);
  const surface = isDark ? "#1B211D" : "#FFFFFF";
  const insets = useSafeAreaInsets();
  const systemNavigationInset = Platform.OS === "android" ? insets.bottom : 0;

  return (
    <Tabs
      initialRouteName="map"
      detachInactiveScreens={false}
      screenOptions={{
        headerShown: false,
        lazy: false,
        freezeOnBlur: false,
        sceneStyle: { backgroundColor: surface },
        animation: "shift",
        transitionSpec: {
          animation: "timing",
          config: { duration: 220 },
        },
        tabBarActiveTintColor: GREEN,
        tabBarInactiveTintColor: "#778078",
        tabBarLabelStyle: { fontSize: 12, fontWeight: "700", marginTop: 2 },
        tabBarStyle: {
          height: 76 + systemNavigationInset,
          paddingTop: 9,
          paddingBottom: 10 + systemNavigationInset,
          borderTopColor: isDark ? "#343D36" : "#E5EBE5",
          backgroundColor: surface,
        },
      }}
    >
      <Tabs.Screen name="index" options={{ href: null }} />
      <Tabs.Screen
        name="map"
        options={{
          title: "홈",
          tabBarIcon: ({ color, size, focused }) => (
            <Ionicons
              name={focused ? "map" : "map-outline"}
              color={color}
              size={size}
            />
          ),
        }}
      />
      <Tabs.Screen
        name="community"
        options={{
          title: "피드",
          tabBarIcon: ({ color, size, focused }) => (
            <Ionicons
              name={focused ? "images" : "images-outline"}
              color={color}
              size={size}
            />
          ),
        }}
      />
      <Tabs.Screen
        name="reservation"
        options={{
          title: "예약",
          tabBarIcon: ({ color, size, focused }) => (
            <Ionicons
              name={focused ? "calendar" : "calendar-outline"}
              color={color}
              size={size}
            />
          ),
        }}
      />
      <Tabs.Screen
        name="profile"
        options={{
          title: "저장",
          href: "/(tabs)/profile/bookmark",
          tabBarIcon: ({ color, size, focused }) => (
            <Ionicons
              name={focused ? "bookmark" : "bookmark-outline"}
              color={color}
              size={size}
            />
          ),
        }}
      />
      <Tabs.Screen name="course" options={{ href: null }} />
    </Tabs>
  );
}
