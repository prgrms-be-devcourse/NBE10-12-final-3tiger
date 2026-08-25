import { Ionicons } from "@expo/vector-icons";
import { PostActions } from "@/components/feed/post-actions";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Text } from "@/components/ui/text";
import { router } from "expo-router";
import { useState } from "react";
import { FlatList, Image, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

const POSTS = [
  {
    id: "1",
    user: "김산책 할아버지",
    time: "2시간 전",
    avatar:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuA5gyewwSk462BPyLv4aUpd6Gu0-57k9gLlQSUlzaJVdzvO04WWCrKT_VLgBNiAvK86rt9rCHCqIrbddBFFW3LS4KUZPPczxbloHqxVWmO-wIajnsYt95Y9GggbLk7KFpZtZs2kYZ-NlKWmaM9bZRBBJFPvXNuWGe9Sw5yG5zQx1jdQPltABOTPAX7D9aBx7gIwo1HptqvoZgAfuWnr4E2aR_2Y8XeCYFL7quxvwlhaVdoJQJQ4ib6itg",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuCQ-xfi1FMqlIjqR4oOIfOxGpSDcrYmdFcnLN8KxrfjSHZBxosM7jZH2SCB-qu22zyp_nXu3c6Yk4AOE6-q9Vv80PthI10oNkU_hv5y_fgnWS1Q2UJBVavXlF8K7Ehzlu2zjebC7nds7b7Qia2lu2K90NfA06nAORAc-0IXNiDOQZRY-008cF5hbaWuucJp4oKl_gr9iKtzd-TfMvjzx3GnRWsK8yr2_CmM_v0muoUQ8qKhVx-P98aA0w",
    likes: 124,
    comments: 12,
    liked: true,
    text: "오늘 날씨가 너무 좋아서 해피랑 서울숲 한 바퀴 돌았습니다. 산책로가 아주 깨끗하게 정비되어 있어서 걷기 편하네요! 🌳🐕",
  },
  {
    id: "2",
    user: "유모차마실",
    time: "4시간 전",
    avatar:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuD-IZJn35Lx1YHUvr47xi468kAlkqc46dVety-iAqzocpO8cGKcTnvjbLTc-PPJGUolCVWDF56uvwkoUsNnA_NWCmZh0UgNXByBb9U3cp76173InP02AyBwnt8P7MXVAlnfMDH59Iv0hQHJ9htyug7HTdLW-kpXJpnI6NACV31rtFz2rNKq7DyXpGvKWm3X9n13tS333PAAz9UXsRNQgqe2QIDCBD-1Y1MXJU_Wg2Mt86EIjkZKiaFhBw",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuB3cAg1BYFLuIc7ZcUm0zORE2sVZ567hHk1ec1oqA1UzIQ0AmehcjWVcHN2tpeRER-2bjuA4sNLD0kWH7PyJmomD9RrOwnX4akBgY9x_kZDwM_g5c29w160tg1PtDtQUASFdBiqFkm35CRpjI1k7BvoTdzj57TbEZBagqhryfyc1nxHPVSp5o47J5v5YuONPuLbnqJP1xsT-r2QUACznmkY-OXJgYkHQOtVkt4G8tBlH66zXwgLsS2Knw",
    likes: 89,
    comments: 5,
    liked: false,
    text: "새로 생긴 공원 길, 경사도 없고 바닥이 매끄러워서 유모차 끌기 정말 좋아요. 아이도 신나하네요. 추천합니다! 👶🌸",
  },
];

type PostItem = (typeof POSTS)[number];

function IconButton({
  label,
  icon,
  onPress,
}: {
  label: string;
  icon: React.ComponentProps<typeof Ionicons>["name"];
  onPress?: () => void;
}) {
  return (
    <Button
      variant="ghost"
      size="icon"
      accessibilityLabel={label}
      className="h-11 w-11 rounded-full active:bg-[#E9F5EC]"
      onPress={onPress}
    >
      <Ionicons name={icon} size={23} color="#087A3F" />
    </Button>
  );
}

function Post({ item }: { item: PostItem }) {
  const [liked, setLiked] = useState(item.liked);
  const [bookmarked, setBookmarked] = useState(false);
  const likeCount = item.likes + (liked === item.liked ? 0 : liked ? 1 : -1);

  return (
    <View className="w-full bg-white">
      <View className="min-h-[72px] flex-row items-center gap-3 px-5 py-3">
        <Avatar
          alt={`${item.user} 프로필`}
          className="h-11 w-11 border border-[#E4EAE5]"
        >
          <AvatarImage source={{ uri: item.avatar }} />
          <AvatarFallback className="bg-[#E9F5EC]">
            <Text className="font-extrabold text-[#087A3F]">
              {item.user.slice(0, 1)}
            </Text>
          </AvatarFallback>
        </Avatar>
        <View className="flex-1">
          <Text className="text-[16px] font-extrabold leading-5 text-[#191C1D]">
            {item.user}
          </Text>
          <Text className="mt-1 text-xs text-[#6B756D]">{item.time}</Text>
        </View>
        <IconButton label="게시글 메뉴" icon="ellipsis-vertical" />
      </View>

      <Image
        source={{ uri: item.image }}
        className="h-80 w-full bg-[#E6E8E9]"
        resizeMode="cover"
      />

      <PostActions
        liked={liked}
        likeCount={likeCount}
        commentCount={item.comments}
        onToggleLike={() => setLiked((value) => !value)}
        bookmarked={bookmarked}
        onToggleBookmark={() => setBookmarked((value) => !value)}
      />

      <Text className="px-5 pb-5 text-[15px] leading-[24px] text-[#252A26]">
        <Text className="text-[16px] font-extrabold leading-[24px] text-[#191C1D]">
          {item.user}{" "}
        </Text>
        {item.text}
      </Text>
      <Separator className="bg-[#E8ECE8]" />
    </View>
  );
}

export default function CommunityScreen() {
  return (
    <SafeAreaView className="flex-1 bg-white" edges={["top"]}>
      <View className="h-14 flex-row items-center justify-between border-b border-[#EEF1EE] bg-white px-3">
        <View
          pointerEvents="none"
          className="absolute inset-x-0 h-14 items-center justify-center"
        >
          <Image
            source={require("../../../assets/title.png")}
            className="h-[35px] w-[132px]"
            resizeMode="contain"
          />
        </View>
        <IconButton
          label="게시글 작성"
          icon="add"
          onPress={() => router.push("/review/write" as never)}
        />
        <View className="ml-auto flex-row">
          <IconButton label="피드 검색" icon="search" />
          <IconButton label="알림" icon="notifications-outline" />
        </View>
      </View>
      <FlatList
        data={POSTS}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => <Post item={item} />}
        showsVerticalScrollIndicator={false}
        contentContainerClassName="pb-6"
        ItemSeparatorComponent={() => <View className="h-3 bg-[#F3F6F3]" />}
        onEndReachedThreshold={0.6}
      />
    </SafeAreaView>
  );
}
