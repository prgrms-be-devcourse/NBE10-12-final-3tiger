import { Switch as NativeSwitch, type SwitchProps } from "react-native";

export function Switch({ value, ...props }: SwitchProps) {
  return (
    <NativeSwitch
      accessibilityRole="switch"
      value={value}
      trackColor={{ false: "#CBD5E1", true: "#22C55E" }}
      thumbColor="#FFFFFF"
      ios_backgroundColor="#CBD5E1"
      {...props}
    />
  );
}
