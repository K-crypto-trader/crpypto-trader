import SwiftUI

struct OrderDetailView: View {
  @EnvironmentObject private var orderViewModel: OrderViewModel
  @EnvironmentObject private var userViewModel: UserViewModel
  
  @Binding var side: OrderSide
  @Binding var orderAction: Bool
  
  @State var selectedOrderType = "지정가"
  @FocusState private var isEditing: Bool

  var body: some View {
    VStack(alignment: .leading, spacing: 0) {
      HStack(spacing: 0) {
        Text("주문유형")
          .padding(.trailing, 52)
        HStack(spacing: 8) {
          RadioButton(text: "지정가", selected: $selectedOrderType)
          RadioButton(text: "시장가", selected: $selectedOrderType)
          RadioButton(text: "예약-지정가", selected: $selectedOrderType)
        }
      }
      .padding(.vertical, 16)
      
      HStack(alignment: .center, spacing: 0) {
        Text("주문가능")
        Spacer()
        Text("0") // from User Model
          .font(.headline)
          .padding(.trailing, 2)
        Text("KRW")
          .padding(0)
      }
      .padding(.top, 4)
      .padding(.bottom, 16)
      
      PriceStepperView(price: $orderViewModel.price, title: "\(side.title)가격 (KRW)")
        .padding(.bottom, 8)
      
      HStack(alignment: .center, spacing: 0) {
        Text("주문수량 (BTC)")
        Spacer()
        HStack(spacing: 0) {
          DoubleTextField(value: $orderViewModel.amount, isEditing: isEditing)
            .padding(.trailing, 12)
            .focused($isEditing)        }
        .frame(width: 320, height: 40)
        .overlay(
          Rectangle()
            .stroke(isEditing ? .blue : Color.gray150, lineWidth: 1)
        )
      }
      .padding(.bottom, 8)
      
      HStack(spacing: 0) {
        Spacer()
        HStack(spacing: 12) {
          ForEach(["10%", "25%", "50%", "100%"], id: \.self) { percent in
            Button(action: {
              // 퍼센트에 맞는 수량 계산 코드 필요
            }) {
              Text(percent)
                .font(.system(size: 12))
                .padding(.vertical, 6)
                .padding(.horizontal, 12)
                .overlay {
                  Rectangle()
                    .stroke(Color.gray200, lineWidth: 1)
                }
            }
            .buttonStyle(PlainButtonStyle())
          }
          
          Button {
            // 직접입력 기능 구현 필요
          } label: {
            Text("직접입력")
              .font(.system(size: 12))
              .padding(.vertical, 6)
              .padding(.horizontal, 12)
              .overlay {
                Rectangle()
                  .stroke(Color.gray200, lineWidth: 1)
              }
          }
          .buttonStyle(PlainButtonStyle())
        }
        .padding(0)
        .frame(width: 320)
      }
      .padding(.bottom, 8)
      
      
      HStack(spacing: 0) {
        Text("주문총액 (KRW)")
        Spacer()
        HStack {
          Spacer()
          Text(
            "\((orderViewModel.total).formattedPrice())"
          ) // price * amount
            .padding(.trailing, 12)
        }
        .frame(width: 320, height: 40)
        .overlay(
          Rectangle()
            .stroke(Color.gray150, lineWidth: 1)
        )
      }
      .frame(height: 40)
      .padding(.bottom, 8)
      
      Button {
        orderAction = true
        // 1. 주문하시겠습니까 창 띄우기
        // 2. 클릭하면 order
//        orderViewModel.order(account: userViewModel.user!.account)
      } label: {
        Text("주문하기")
          .foregroundStyle(.white)
          .frame(maxWidth: .infinity)
          .frame(height: 40)
          .background(side.color)
          .contentShape(Rectangle())
      }
      .buttonStyle(PlainButtonStyle())
    }
    .padding(.horizontal, 12)
    .padding(.bottom, 12)
  }
}
