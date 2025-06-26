#!/bin/bash
#
#echo "Starting /suggestCategories progressive testing..."
#
#BASE_URL="http://localhost:8080/suggestCategories"
#
## Stage 1: Select "Music"
#echo "Stage 1: selected=Music"
#curl -G "$BASE_URL" --data-urlencode "selected=Music"
#echo -e "\n------------------------"
#
## Stage 2: Add "Technology"
#echo "Stage 2: selected=Music,Technology"
#curl -G "$BASE_URL" --data-urlencode "selected=Music" --data-urlencode "selected=Technology"
#echo -e "\n------------------------"
#
## Stage 3: Add "Science"
#echo "Stage 3: selected=Music,Technology,Science"
#curl -G "$BASE_URL" --data-urlencode "selected=Music" --data-urlencode "selected=Technology" --data-urlencode "selected=Science"
#echo -e "\n------------------------"
#
## Stage 4: Exclude "Sports"
#echo "Stage 4: selected=Music,Technology,Science excluded=Sports"
#curl -G "$BASE_URL" --data-urlencode "selected=Music" --data-urlencode "selected=Technology" --data-urlencode "selected=Science" --data-urlencode "excluded=Sports"
#echo -e "\n------------------------"
#
## Stage 5: Add "Art", "Literature"
#echo "Stage 5: selected=Music,Technology,Science,Art,Literature"
#curl -G "$BASE_URL" --data-urlencode "selected=Music" --data-urlencode "selected=Technology" --data-urlencode "selected=Science" --data-urlencode "selected=Art" --data-urlencode "selected=Literature"
#echo -e "\n------------------------"
#
#echo "Completed /suggestCategories curl test."
#


## Base URL of your server
#BASE_URL="http://localhost:8080/signup"
#
## Number of users to create
#USER_COUNT=20
#
#for i in $(seq 1 $USER_COUNT); do
#  EMAIL="user${i}@example.com"
#  PASSWORD="password${i}"
#
#  echo "Signing up user $EMAIL..."
#
#  curl -X POST $BASE_URL \
#    -H "Content-Type: application/json" \
#    -d '{
#      "email": "'"$EMAIL"'",
#      "password": "'"$PASSWORD"'"
#    }'
#
#  echo -e "\n----------------------------"
#done
#
#echo "Finished creating $USER_COUNT users."
#
#

#!/bin/bash
#
## Base URL of your server
#BASE_URL="http://localhost:8080/userInterests"
#
## Function to generate a random list of unique interest IDs (from 1 to 7)
#generate_interest_ids() {
#  # Generate a random number of interests (between 3 and 5)
#  num_interests=$(( ( RANDOM % 3 ) + 3 ))
#
#  # Use jot to generate unique IDs, then sort randomly
#  ids=$(jot - 1 7 | sort -R | head -n $num_interests | tr '\n' ',' | sed 's/,$//')
#
#  echo "$ids"
#}
#
## Assign interests for users 1 to 7
#for user_id in {81..87}; do
#  INTEREST_IDS=$(generate_interest_ids)
#
#  echo "Adding interests for user $user_id: $INTEREST_IDS"
#
#  curl -X POST $BASE_URL \
#    -H "Content-Type: application/json" \
#    -d '{
#      "userId": '"$user_id"',
#      "interestIds": ['"$INTEREST_IDS"']
#    }'
#
#  echo -e "\n----------------------------"
#done
#
#echo "Finished adding interests for 7 users."

#!/bin/bash

# Base URL of your server
#BASE_URL="http://localhost:8080/recommendUsers"
#
## User ID to recommend for
#USER_ID=81
#
#echo "Requesting user recommendations for user $USER_ID..."
#
#curl -X GET "$BASE_URL/$USER_ID" \
#  -H "Content-Type: application/json"
#
#echo -e "\n----------------------------"
#echo "Finished recommending users for user $USER_ID."

#!/bin/bash

## Base URL
#URL="http://localhost:8080/createCommunity"
#
## Base directory where your test images are stored
#BASE_DIR="$(cd "$(dirname "$0")/test_images" && pwd)"
#
## Community 1
#curl -X POST "$URL" \
#  -H "Content-Type: multipart/form-data" \
#  -F "data={\"name\":\"Community One\",\"description\":\"First test\",\"rules\":[\"Rule1\",\"Rule2\"],\"membershipType\":1,\"creatorUserId\":81};type=application/json" \
#  -F "profilePicture=@$BASE_DIR/a.jpg" \
#  -F "coverPhoto=@$BASE_DIR/d.jpg"
#
## Community 2
#curl -X POST "$URL" \
#  -H "Content-Type: multipart/form-data" \
#  -F "data={\"name\":\"Community Two\",\"description\":\"Second test\",\"rules\":[\"RuleA\",\"RuleB\"],\"membershipType\":2,\"creatorUserId\":81};type=application/json" \
#  -F "profilePicture=@$BASE_DIR/c.png" \
#  -F "coverPhoto=@$BASE_DIR/d.jpg"
#
## Community 3
#curl -X POST "$URL" \
#  -H "Content-Type: multipart/form-data" \
#  -F "data={\"name\":\"Community Three\",\"description\":\"Third test\",\"rules\":[\"RuleX\",\"RuleY\"],\"membershipType\":3,\"creatorUserId\":83};type=application/json" \
#  -F "profilePicture=@$BASE_DIR/e.png" \
#  -F "coverPhoto=@$BASE_DIR/d.jpg"
#
## Community 4
#curl -X POST "$URL" \
#  -H "Content-Type: multipart/form-data" \
#  -F "data={\"name\":\"Community Four\",\"description\":\"Fourth test\",\"rules\":[\"RuleAlpha\",\"RuleBeta\"],\"membershipType\":1,\"creatorUserId\":84};type=application/json" \
#  -F "profilePicture=@$BASE_DIR/d.jpg" \
#  -F "coverPhoto=@$BASE_DIR/b.jpg"
#
#echo "Done testing 4 communities."
#
#
##!/bin/bash

## Base URL
#BASE_URL="http://localhost:8080/follow"
#
## Number of users to simulate (at least 12)
#NUM_USERS=12
#
## User ID range
#MIN_USER_ID=79
#MAX_USER_ID=100
#
#echo "Triggering random follows..."
#
#for ((i=1; i<=NUM_USERS; i++)); do
#    userId=$((RANDOM % (MAX_USER_ID - MIN_USER_ID + 1) + MIN_USER_ID))
#    targetUserId=$((RANDOM % (MAX_USER_ID - MIN_USER_ID + 1) + MIN_USER_ID))
#
#    # Ensure userId and targetUserId are not the same
#    while [ "$userId" -eq "$targetUserId" ]; do
#        targetUserId=$((RANDOM % (MAX_USER_ID - MIN_USER_ID + 1) + MIN_USER_ID))
#    done
#
#    echo "User $userId follows User $targetUserId"
#
#    curl -X POST "$BASE_URL" \
#        -H "Content-Type: application/json" \
#        -d "{\"userId\": $userId, \"targetUserId\": $targetUserId}"
#
#    echo -e "\n--------------------------------------"
#done
#
#echo "Random follow actions completed."


#!/bin/bash

#!/bin/bash

### Base URL
#URL="http://localhost:8080/createCommunity"
#
### Directory where your test images are stored (relative to this script)
#BASE_DIR="$(cd "$(dirname "$0")/test_images" && pwd)"
#
### Image pool
#IMAGES=("a.jpg" "b.jpg" "c.png" "d.jpg" "e.png")
#
### Generates random JSON list of tag IDs (2–4 unique numbers from 1–14)
#generate_tags_json() {
#  local count=$((RANDOM % 3 + 2)) # 2 to 4 tags
#  local tags=()
#  while [ "${#tags[@]}" -lt "$count" ]; do
#    tag=$((RANDOM % 14 + 1))
#    already_added=false
#    for t in "${tags[@]}"; do
#      if [[ "$t" == "$tag" ]]; then
#        already_added=true
#        break
#      fi
#    done
#    if ! $already_added; then
#      tags+=("$tag")
#    fi
#      done
#  printf "[%s]\n" "$(IFS=,; echo "${tags[*]}")"
#}
#
### Community creation loop
#for i in {1..10}; do
#  NAME="Community_$i"
#  DESC="Description for $NAME"
#  RULES_JSON='["No hate","No spam","Respect all members"]'
#  TAGS_JSON=$(generate_tags_json)
#  TYPE=$((RANDOM % 3 + 1))              # membershipType 1–3
#  CREATOR=$((RANDOM % 4 + 81))          # userId 81–84
#  PROFILE=${IMAGES[$RANDOM % ${#IMAGES[@]}]}
#  COVER=${IMAGES[$RANDOM % ${#IMAGES[@]}]}
#
#  echo "Creating: $NAME with tags: $TAGS_JSON, profile: $PROFILE, cover: $COVER, creator: $CREATOR"
#
#  curl -s -X POST "$URL" \
#    -H "Content-Type: multipart/form-data" \
#    -F "data={\"name\":\"$NAME\",\"description\":\"$DESC\",\"rules\":$RULES_JSON,\"categoryTags\":$TAGS_JSON,\"membershipType\":$TYPE,\"creatorUserId\":$CREATOR};type=application/json" \
#    -F "profilePicture=@$BASE_DIR/$PROFILE" \
#    -F "coverPhoto=@$BASE_DIR/$COVER"
#
#  echo -e "\n---"
#done
#
#echo "✅ Done creating 10 communities."
#
#


#URL="http://localhost:8080/addCommunityMembers"
#ALL_USERS=($(seq 80 99))  # valid userId pool
#TOTAL_COMMUNITIES=14
#
#for COMMUNITY_ID in $(seq 1 $TOTAL_COMMUNITIES); do
#  echo "📩 Adding users to Community $COMMUNITY_ID → "
#
#  # Pick 3 random user IDs from ALL_USERS
#  SELECTED_USERS=($(printf "%s\n" "${ALL_USERS[@]}" | sort -R | head -n 3))
#
#  # Random addedByUserId from 79 to 99
#  ADDED_BY_USER_ID=$((RANDOM % 21 + 79))  # (0–20) + 79
#
#  # JSON array string
#  USER_IDS_JSON=$(printf ",%s" "${SELECTED_USERS[@]}")
#  USER_IDS_JSON="[${USER_IDS_JSON:1}]"
#
#  # Final JSON payload
#  JSON_PAYLOAD=$(cat <<EOF
#{
#  "communityId": $COMMUNITY_ID,
#  "addedByUserId": $ADDED_BY_USER_ID,
#  "userIds": $USER_IDS_JSON
#}
#EOF
#)
#
#  # Send POST request
#  curl -s -X POST "$URL" \
#    -H "Content-Type: application/json" \
#    -d "$JSON_PAYLOAD"
#
#  echo -e "\n---"
#done
#
#echo "✅ Done populating community members."


#!/bin/bash

#URL="http://localhost:8080/createPost"
#MEDIA_DIR="$HOME/Downloads"
#IMAGES=("a.jpg" "b.jpg" "d.jpg" "e.jpg" "f.jpg" "z.png" "p.png" "News.png" "freee.png")
#VIDEO="video.mov"
#
#
## Topics to randomize across
#TOPICS=("software engineering" "science" "technology" "music" "arts" "politics" "gaming" "culture" "startup" "Lagos life" "hustle" "education" "funny" "power supply" "AFROBEATS")
#
## Sample sentences pool (will combine to form a post)
#SENTENCES=(
#"This country ehn,\nyou need strong mind to code under this heat."
#
#"Did you know Lagos has more software engineers than Nairobi?\nNo light, but plenty of JavaScript."
#
#"Jollof rice no be beans,\nbut AI go soon cook am with ChatGPT 😅."
#
#"I just launched my app...\nand NEPA took light.\nClassic Naija dev vibes!"
#
#"Gaming in Nigeria with 200ms latency\nis a skill on its own.\nWe no dey play, we dey endure."
#
#"Our politicians go use grammar confuse everybody,\nbut nothing dey work.\nWhich kind wahala be this?"
#
#"If you’ve survived PHCN for 10 years,\nyou fit survive startup stress with joy."
#
#"My startup pitch ended when the Zoom froze.\nThanks to our 'fast' 4G internet. 🙃"
#
#"AFROBEATS to the world!\nBut where's the support for local music infrastructure?\nMake dem no use us finish."
#
#"Scientists in Nigeria dey try,\nbut funding go wound person.\nWe dey do research with generator noise."
#
#"Even ChatGPT no fit understand Nigerian network wahala sometimes.\nYou say ‘retry’, we say ‘Glo’."
#
#"Building tech from Aba is a whole flex.\nBootstrap no be beans.\nRespect the hustle!"
#
#"Art dey flourish for Naija,\nbut gallery dey inside WhatsApp group.\nWe move!"
#
#"This new tech bill go affect startups again.\nWhy always us?\nUna want make we dey code inside prison ni?"
#
#"Music streaming platforms dey collect subscription,\nbut artists dey beg for royalty statements.\nWetin dey sup?"
#
#"Nigerian culture too rich abeg.\nFrom Afrobeats to Ankara to pepper soup —\nwe dey vibrate on all levels."
#
#"My code finally worked.\nNEPA said 'not today' 😭\nI go cry."
#
#"We need to teach programming in pidgin:\n'If x dey greater than y, then do am'.\nGo sweet die."
#
#"Politics here na movie.\nSeason 12 just start\nand dem don already introduce 5 new villains. 🍿"
#
#"Gaming rig setup: ₦500K.\nStill no light to enjoy am.\nSapa no go kill person."
#
#"Science students dey innovate,\nbut lab equipment dey miss pass lecturer.\nNa dry theory full everywhere."
#
#"Naija devs dey learn React for free online,\nthen NEPA go show dem shege mid-build."
#
#"Today’s coding vibes:\nNo bugs, no light,\nand plenty AC noise from the generator.\nStill we move."
#
#"Dem talk say Nigeria get talent,\nbut no tools.\nTrue true, we dey build spaceship with cutlass."
#
#"Just imagine light 24/7 for one week.\nDevelopers go think say dem don migrate abroad."
#
#)
#
#
#for i in $(seq 1 100); do
#  # 🔀 Random userId between 79 and 102
#  USER_ID=$((RANDOM % 24 + 79))
#
#  # Randomly build post text (you already have SENTENCES defined)
#  num_sent=$((RANDOM % 3 + 2))
#  post_text=""
#  for _ in $(seq 1 $num_sent); do
#    post_text+="${SENTENCES[$RANDOM % ${#SENTENCES[@]}]} "
#    if (( RANDOM % 2 == 0 )); then
#      post_text+="\n"
#    fi
#  done
#  topic="${TOPICS[$RANDOM % ${#TOPICS[@]}]}"
#  post_text+="\n#${topic// /}"
#
#  json_payload=$(jq -n --arg userId "$USER_ID" --arg text "$post_text" '{userId: ($userId|tonumber), textContent: $text}')
#
#  # Attach media
#  part_args=("-F" "data=$json_payload")
#  mode=$((RANDOM % 4))
#  case $mode in
#    0) ;;  # no media
#    1)
#      num_imgs=$((RANDOM % 3 + 1))
#      for j in $(seq 1 $num_imgs); do
#        img="${IMAGES[$RANDOM % ${#IMAGES[@]}]}"
#        part_args+=("-F" "image$j=@$MEDIA_DIR/$img")
#      done
#      ;;
#    2)
#      part_args+=("-F" "video=@$MEDIA_DIR/$VIDEO")
#      ;;
#    3)
#      for j in $(seq 1 $((RANDOM % 2 + 1))); do
#        img="${IMAGES[$RANDOM % ${#IMAGES[@]}]}"
#        part_args+=("-F" "image$j=@$MEDIA_DIR/$img")
#      done
#      part_args+=("-F" "video=@$MEDIA_DIR/$VIDEO")
#      ;;
#  esac
#
#  echo "[$i] Sending post from userId=$USER_ID, mode=$mode"
#  curl -s -X POST "$URL" "${part_args[@]}" >/dev/null
#done
#
#echo "✅ All 100 posts submitted across users 79–102."
#

##!/bin/bash

#BASE_URL="http://localhost:8080"
#USER_IDS=($(seq 79 102))
#POST_IDS=($(seq 86 187))
#
## Sample comment messages
#COMMENTS=(
#  "This is deep 🔥"
#  "Well said! Totally agree."
#  "Na true you talk o 😂"
#  "Insightful. I’m sharing this!"
#  "Wahala for who no sabi code like this!"
#  "Make dem pin this post abeg!"
#  "Big facts.\nEspecially that last line."
#  "You just spoke my mind."
#  "Omo, this one choke!"
#  "Solid write-up 👏👏"
#)
#
#get_random_user() {
#  echo "${USER_IDS[$RANDOM % ${#USER_IDS[@]}]}"
#}
#
#get_random_comment() {
#  echo "${COMMENTS[$RANDOM % ${#COMMENTS[@]}]}"
#}
#
## Send like
#send_like() {
#  curl -s -X POST "$BASE_URL/likePost" \
#    -H "Content-Type: application/json" \
#    -d "{\"postId\": $1, \"userId\": $2}" >/dev/null
#}
#
## Send view
#send_view() {
#  curl -s -X POST "$BASE_URL/addView" \
#    -H "Content-Type: application/json" \
#    -d "{\"postId\": $1, \"userId\": $2}" >/dev/null
#}
#
## Add comment
#send_comment() {
#  local parent=$3
#  if [ "$parent" == "null" ]; then
#    data="{\"postId\": $1, \"userId\": $2, \"commentText\": \"$(get_random_comment)\"}"
#  else
#    data="{\"postId\": $1, \"userId\": $2, \"commentText\": \"$(get_random_comment)\", \"parentCommentId\": $parent}"
#  fi
#  curl -s -X POST "$BASE_URL/addComment" \
#    -H "Content-Type: application/json" \
#    -d "$data" >/dev/null
#}
#
## Quote repost
#send_quote_repost() {
#  curl -s -X POST "$BASE_URL/quote-repost" \
#    -H "Content-Type: application/json" \
#    -d "{\"postId\": $1, \"userId\": $2, \"comment\": \"Interesting angle to repost this.\"}" >/dev/null
#}
#
## Bookmark
#send_bookmark() {
#  curl -s -X POST "$BASE_URL/addBookmark" \
#    -H "Content-Type: application/json" \
#    -d "{\"postId\": $1, \"userId\": $2}" >/dev/null
#}
#
## Loop through all posts
#for postId in "${POST_IDS[@]}"; do
#  echo "➤ Populating post $postId"
#
#  numLikes=$((RANDOM % 80 + 20))
#  numViews=$((RANDOM % 120 + 30))
#  numComments=$((RANDOM % 50 + 10))
#  numReposts=$((RANDOM % 15))
#  numBookmarks=$((RANDOM % 20))
#
#  for _ in $(seq 1 $numLikes); do
#    send_like "$postId" "$(get_random_user)"
#  done
#
#  for _ in $(seq 1 $numViews); do
#    send_view "$postId" "$(get_random_user)"
#  done
#
#  parentIds=()
#  for _ in $(seq 1 $numComments); do
#    userId=$(get_random_user)
#    if (( RANDOM % 3 == 0 )) && [ "${#parentIds[@]}" -gt 0 ]; then
#      # nested comment
#      parent="${parentIds[$RANDOM % ${#parentIds[@]}]}"
#      send_comment "$postId" "$userId" "$parent"
#    else
#      # top-level comment
#      send_comment "$postId" "$userId" null
#      # fake adding to parent list
#      parentIds+=($((RANDOM % 190 + 1)))
#    fi
#  done
#
#  for _ in $(seq 1 $numReposts); do
#    send_quote_repost "$postId" "$(get_random_user)"
#  done
#
#  for _ in $(seq 1 $numBookmarks); do
#    send_bookmark "$postId" "$(get_random_user)"
#  done
#
#done

#echo "✅ Done adding metrics to posts."

#!/bin/bash

# === Configuration ===
BASE_URL="http://localhost:8080"
SET_PROFILE_ENDPOINT="$BASE_URL/set_basic_profile"
UPLOAD_PICTURE_ENDPOINT="$BASE_URL/upload_profile_picture"

MEDIA_DIR="$HOME/Downloads"
IMAGES=("a.jpg" "b.jpg" "d.jpg" "e.jpg" "f.jpg" "z.png" "p.png" "News.png" "freee.png")

# Sample profile info (you can randomize more fields if needed)
FIRST_NAMES=("Alex" "Jamie" "Chris" "Taylor" "Jordan" "Morgan" "Casey" "Riley" "Avery" "Parker")
LAST_NAMES=("Smith" "Johnson" "Williams" "Brown" "Jones" "Garcia" "Miller" "Davis" "Lopez" "Wilson")
USERNAMES=("alpha" "beta" "gamma" "delta" "omega" "nova" "zeus" "hera" "thor" "loki")
BIO="This is a sample short bio"
ABOUT="This is a sample about section"
GENDER="MALE" # or FEMALE

# === Loop over user IDs ===
for ((userId=83; userId<=102; userId++)); do
    echo "👉 Setting up profile for user ID: $userId"

    # Pick random names and usernames
    firstName=${FIRST_NAMES[$RANDOM % ${#FIRST_NAMES[@]}]}
    lastName=${LAST_NAMES[$RANDOM % ${#LAST_NAMES[@]}]}
    username="${USERNAMES[$RANDOM % ${#USERNAMES[@]}]}$userId" # Unique username

    # --- 1. Set Basic Profile ---
    echo "📤 Sending basic profile request..."
    curl -s -X POST "$SET_PROFILE_ENDPOINT" \
        -H "Content-Type: application/json" \
        -d '{
            "userId": '"$userId"',
            "firstName": "'"$firstName"'",
            "lastName": "'"$lastName"'",
            "username": "'"$username"'",
            "shortBio": "'"$BIO"'",
            "about": "'"$ABOUT"'",
            "gender": "'"$GENDER"'"
        }'

    echo -e "\n✅ Basic profile set for user $userId"

    # --- 2. Upload Profile Picture ---
    IMAGE_FILE="${IMAGES[$((userId % ${#IMAGES[@]}))]}"
    IMAGE_PATH="$MEDIA_DIR/$IMAGE_FILE"

    if [ ! -f "$IMAGE_PATH" ]; then
        echo "⚠️  Image file not found: $IMAGE_PATH — skipping upload"
        continue
    fi

    echo "📸 Uploading profile picture: $IMAGE_FILE"
    curl -s -X POST "$UPLOAD_PICTURE_ENDPOINT" \
        -F "userId=$userId" \
        -F "file=@$IMAGE_PATH"

    echo -e "\n✅ Profile picture uploaded for user $userId"
    echo "---------------------------------------"
done
